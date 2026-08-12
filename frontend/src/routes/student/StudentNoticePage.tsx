import { NoticeBoard } from "../../shared/notice/NoticeBoard";

/** 학생 본인 기준. studentId를 넘기지 않으면 서버가 토큰에서 찾는다. */
export default function StudentNoticePage() {
  return <NoticeBoard />;
}
