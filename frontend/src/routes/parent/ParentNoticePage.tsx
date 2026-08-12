import { useSelectedChild } from "../../shared/auth/SelectedChildContext";
import { NoticeBoard } from "../../shared/notice/NoticeBoard";

/**
 * 자녀 기준 공지. 반 범위 공지가 자녀마다 다르므로 studentId를 넘긴다.
 * 남의 자녀 id를 넣으면 서버가 403이다.
 */
export default function ParentNoticePage() {
  const { selectedStudentId } = useSelectedChild();

  if (selectedStudentId === null) {
    return <p className="text-sm text-slate-400">불러오는 중…</p>;
  }
  return <NoticeBoard studentId={selectedStudentId} />;
}
