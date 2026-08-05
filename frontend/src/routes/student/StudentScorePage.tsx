import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { useAuth } from "../../shared/auth/AuthContext";
import { ExamDdayList } from "../../shared/components/ExamDdayList";
import { ScoreSectionList } from "../../shared/components/ScoreSectionList";
import { getMe, getMyScores, listMyExamSchedules } from "./api";

/**
 * S-7. 내 정보 카드 + 성적.
 *
 * <p>학부모 화면(P-4)과 <b>같은 응답·같은 컴포넌트</b>를 쓴다. 두 화면이
 * ScoreSectionList를 공유하는 것이 "학생도 똑같이 본다"의 보장이다.
 */
export default function StudentScorePage() {
  const { signOut } = useAuth();
  const me = useQuery({ queryKey: ["student", "me"], queryFn: getMe });
  const scores = useQuery({ queryKey: ["student", "scores"], queryFn: () => getMyScores() });
  const exams = useQuery({
    queryKey: ["student", "exam-schedules"],
    queryFn: listMyExamSchedules,
  });

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-900">내 정보 · 성적</h2>

      {me.data && (
        <section className="space-y-1 rounded-xl bg-white p-4 shadow-sm">
          <p className="text-base font-semibold text-slate-900">{me.data.name}</p>
          <p className="text-sm text-slate-600">
            {me.data.classRooms.length === 0
              ? "배정된 반이 없습니다"
              : me.data.classRooms.map((room) => room.name).join(" · ")}
          </p>
          {me.data.phone && <p className="text-xs text-slate-500">{me.data.phone}</p>}

          {/* 학생이 알아야 선생님께 문의해 조치가 된다 */}
          {!me.data.parentLinked && (
            <p className="mt-2 rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
              보호자 계정이 아직 연결되지 않았습니다. 선생님께 문의해 주세요.
            </p>
          )}
        </section>
      )}

      {exams.data && <ExamDdayList schedules={exams.data} />}

      {scores.isPending || !scores.data ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : (
        <ScoreSectionList data={scores.data} />
      )}

      {/*
        학생의 유일한 로그아웃 경로다. 공용 PC나 형제 폰에서 쓰는 경우가 있어
        이게 없으면 계정을 내려놓을 방법이 없다. 배치는 학부모 화면(P-4)과 맞췄다.
      */}
      <div className="flex items-center justify-between pt-2 text-sm">
        <Link to="/privacy" className="text-slate-500 underline">
          개인정보처리방침
        </Link>
        <button type="button" onClick={() => void signOut()} className="text-slate-500 underline">
          로그아웃
        </button>
      </div>
    </div>
  );
}
