// 비밀번호를 명령줄 인수에 넣지 않고 Newman에 메모리로 넘깁니다(프로세스 목록에 남지 않게).
// 사용: PowerShell에서 STOCKDROP_*_PASSWORD 환경변수를 설정한 뒤 `npx -y -p newman@6.2.1 node run-newman.js`
const path = require('path');
const newman = require('newman');

const required = ['BUYER', 'SELLER', 'ADMIN'];
const missing = required.filter((r) => !process.env[`STOCKDROP_${r}_PASSWORD`]);
if (missing.length) {
  console.error(`비밀번호 환경변수가 없습니다: ${missing.map((r) => `STOCKDROP_${r}_PASSWORD`).join(', ')}`);
  process.exit(2);
}

newman.run(
  {
    collection: path.join(__dirname, 'STOCKDROP-order-guard.postman_collection.json'),
    environment: path.join(__dirname, 'STOCKDROP-order-guard.local.postman_environment.json'),
    envVar: [
      { key: 'buyerPassword', value: process.env.STOCKDROP_BUYER_PASSWORD },
      { key: 'sellerPassword', value: process.env.STOCKDROP_SELLER_PASSWORD },
      { key: 'adminPassword', value: process.env.STOCKDROP_ADMIN_PASSWORD },
    ],
    reporters: 'cli',
  },
  (err, summary) => {
    if (err) {
      console.error(err.message);
      process.exit(1);
    }
    process.exit(summary.run.failures.length ? 1 : 0);
  }
);
