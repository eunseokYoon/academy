/// 앱바·로고에 들어가는 이름. 2026-08-11 확정.
///
/// 로고와 같은 잠금이라 두 조각으로 나눠 둔다 — 한글은 흰색, `LAB` 만 주황이다.
/// 화면에 그릴 때는 [Wordmark] 가 조립하고, 한 덩어리로 필요할 때는
/// [academyName] 을 쓴다. 붙여 쓰는 이유는 상호가 "남지원영어LAB" 이라
/// 사이에 공백이 없기 때문이다.
///
/// **웹 `frontend/src/shared/branding.ts` 가 정본이다.**
const String academyNameHead = '남지원영어';
const String academyNameTail = 'LAB';
const String academyName = '$academyNameHead$academyNameTail';
