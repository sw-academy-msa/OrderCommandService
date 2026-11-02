package com.msa.order.domain.image.command.service.impl;

import static org.assertj.core.api.Assertions.*;

import com.msa.order.domain.image.command.dto.request.ImageRequest;
import com.msa.order.domain.image.command.service.InquiryImageService;
import com.msa.order.domain.image.repository.InquiryImageRepository;
import com.msa.order.domain.inquiry.command.dto.request.InquiryRequests;
import com.msa.order.domain.inquiry.command.service.OrderInquiryService;
import com.msa.order.domain.order.entity.*;
import com.msa.order.domain.order.repository.OrderRepository;
import com.msa.order.global.config.database.OrderDatabaseTestSupport;
import com.msa.order.global.error.BusinessException;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;

/** PostgreSQL/S3Mock 실제 PUT·서명 검사·불변 복사·소유권·고아 정리를 검증합니다. */
@SpringBootTest
@ActiveProfiles("test")
@Import(OrderDatabaseTestSupport.Time.class)
class InquiryImageServiceImplTest extends OrderDatabaseTestSupport {
    static final GenericContainer<?> S3 =
            new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
                    .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", "order-test-images")
                    .withExposedPorts(9090)
                    .waitingFor(
                            Wait.forHttp("/favicon.ico")
                                    .forPort(9090)
                                    .withStartupTimeout(Duration.ofSeconds(120)));

    static {
        S3.start();
    }

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry r) {
        r.add("order.images.enabled", () -> "true");
        r.add("order.images.local", () -> "true");
        r.add("order.images.bucket", () -> "order-test-images");
        r.add(
                "order.images.endpoint",
                () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(9090));
    }

    @Autowired InquiryImageService images;
    @Autowired InquiryImageRepository imageRepository;
    @Autowired OrderRepository orders;
    @Autowired OrderInquiryService inquiries;
    @Autowired TransactionTemplate tx;
    @Autowired JdbcTemplate jdbc;
    @Autowired MutableClock clock;
    UUID buyer;
    UUID seller;
    UUID order;
    byte[] png =
            Base64.getDecoder()
                    .decode(
                            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aN0cAAAAASUVORK5CYII=");

    @BeforeEach
    void setup() {
        clock.set(Instant.parse("2030-01-01T00:00:00Z"));
        buyer = UUID.randomUUID();
        seller = UUID.randomUUID();
        order =
                tx.execute(
                        s -> {
                            var o =
                                    new PurchaseOrder(
                                            buyer,
                                            UUID.randomUUID(),
                                            "synthetic",
                                            UUID.randomUUID(),
                                            clock.instant());
                            o.sellerId = seller;
                            o.state = PurchaseOrder.State.CONFIRMED;
                            return orders.saveAndFlush(o).id;
                        });
    }

    void put(String url, byte[] data, String type) throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            var response =
                    http.send(
                            HttpRequest.newBuilder(URI.create(url))
                                    .timeout(Duration.ofSeconds(10))
                                    .header("Content-Type", type)
                                    .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                                    .build(),
                            HttpResponse.BodyHandlers.discarding());
            assertThat(response.statusCode()).isBetween(200, 299);
        }
    }

    byte[] get(String url) throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            return http.send(
                            HttpRequest.newBuilder(URI.create(url)).GET().build(),
                            HttpResponse.BodyHandlers.ofByteArray())
                    .body();
        }
    }

    @Test
    void uploadVerificationCopyAndInquiryOwnership() throws Exception {
        var grant = images.issue(buyer, order, new ImageRequest("image/png", png.length));
        put(grant.url(), png, "image/png");
        images.complete(buyer, order, grant.id());
        assertThatThrownBy(() -> images.download(seller, order, grant.id(), true))
                .isInstanceOf(BusinessException.class);
        var inquiry =
                inquiries.create(
                        buyer,
                        order,
                        new InquiryRequests.Create(
                                UUID.randomUUID(), "하자", "합성 사진 문의", List.of(grant.id())));
        assertThat(inquiry.imageIds()).containsExactly(grant.id());
        assertThat(get(images.download(seller, order, grant.id(), true).url())).isEqualTo(png);
        byte[] changed = png.clone();
        changed[changed.length - 1] ^= 1;
        put(grant.url(), changed, "image/png");
        images.complete(buyer, order, grant.id());
        assertThat(get(images.download(buyer, order, grant.id(), false).url())).isEqualTo(png);
    }

    @Test
    void mimeSpoofAndOversizeAreRejected() throws Exception {
        assertThatThrownBy(() -> images.issue(buyer, order, new ImageRequest("image/svg+xml", 100)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(
                        () -> images.issue(buyer, order, new ImageRequest("image/png", 10485761)))
                .isInstanceOf(BusinessException.class);
        var grant = images.issue(buyer, order, new ImageRequest("image/png", 20));
        put(grant.url(), new byte[20], "image/png");
        assertThatThrownBy(() -> images.complete(buyer, order, grant.id()))
                .isInstanceOf(BusinessException.class);
        assertThat(imageRepository.findById(grant.id()).orElseThrow().verified).isFalse();
    }

    @Test
    void pendingLimitDiscardAndExpiredPermissionAreEnforced() {
        var grants = new ArrayList<UUID>();
        for (int i = 0; i < 5; i++) {
            grants.add(images.issue(buyer, order, new ImageRequest("image/png", png.length)).id());
        }
        assertThatThrownBy(
                        () -> images.issue(buyer, order, new ImageRequest("image/png", png.length)))
                .isInstanceOf(BusinessException.class);
        images.discard(buyer, order, grants.getFirst());
        assertThat(images.issue(buyer, order, new ImageRequest("image/png", png.length)))
                .isNotNull();
        assertThatThrownBy(() -> images.complete(buyer, order, grants.getFirst()))
                .isInstanceOf(BusinessException.class);
        clock.set(clock.instant().plusSeconds(301));
        assertThatThrownBy(() -> images.complete(buyer, order, grants.get(1)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void anotherBuyerOrAnotherOrderCannotAttachOrReadImage() throws Exception {
        var grant = images.issue(buyer, order, new ImageRequest("image/png", png.length));
        put(grant.url(), png, "image/png");
        images.complete(buyer, order, grant.id());
        assertThatThrownBy(() -> images.complete(UUID.randomUUID(), order, grant.id()))
                .isInstanceOf(BusinessException.class);
        UUID other =
                tx.execute(
                        s -> {
                            var o =
                                    new PurchaseOrder(
                                            buyer,
                                            UUID.randomUUID(),
                                            "other",
                                            UUID.randomUUID(),
                                            clock.instant());
                            o.sellerId = seller;
                            o.state = PurchaseOrder.State.CONFIRMED;
                            return orders.saveAndFlush(o).id;
                        });
        long before =
                jdbc.queryForObject("select count(*) from orders.order_inquiries", Long.class);
        assertThatThrownBy(
                        () ->
                                inquiries.create(
                                        buyer,
                                        other,
                                        new InquiryRequests.Create(
                                                UUID.randomUUID(),
                                                "하자",
                                                "합성 문의",
                                                List.of(grant.id()))))
                .isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("select count(*) from orders.order_inquiries", Long.class))
                .isEqualTo(before);
        assertThat(imageRepository.findById(grant.id()).orElseThrow().inquiryId).isNull();
    }

    @Test
    void cleanupRemovesOrphansButPreservesLinkedFinalImage() throws Exception {
        var linked = images.issue(buyer, order, new ImageRequest("image/png", png.length));
        put(linked.url(), png, "image/png");
        images.complete(buyer, order, linked.id());
        inquiries.create(
                buyer,
                order,
                new InquiryRequests.Create(UUID.randomUUID(), "하자", "합성 문의", List.of(linked.id())));
        var orphan = images.issue(buyer, order, new ImageRequest("image/png", png.length));
        put(orphan.url(), png, "image/png");
        images.complete(buyer, order, orphan.id());
        clock.set(clock.instant().plus(Duration.ofHours(25)));
        images.cleanup(linked.id());
        images.cleanup(orphan.id());
        assertThat(imageRepository.findById(linked.id()).orElseThrow().cleaned).isTrue();
        assertThat(imageRepository.findById(orphan.id()).orElseThrow().cleaned).isTrue();
        assertThat(get(images.download(seller, order, linked.id(), true).url())).isEqualTo(png);
    }
}
