import { useQuery } from "@tanstack/react-query";
import { ExamDdayList } from "../../shared/components/ExamDdayList";
import { ScoreSections } from "../../shared/components/ScoreSections";
import { WordScoreChart } from "../../shared/components/WordScoreChart";
import { getMe, getMyScores, listMyExamSchedules } from "./api";

/** S-7. 내 정보 카드 + 성적. 학부모 화면(P-4)과 같은 데이터 형식을 쓴다. */
export default function StudentScorePage() {
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
        <>
          <WordScoreChart points={scores.data.word.points} />
          <ScoreSections internal={scores.data.internal} mock={scores.data.mock} />
        </>
      )}
    </div>
  );
}
