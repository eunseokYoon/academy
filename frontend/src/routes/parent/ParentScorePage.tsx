import { useQuery } from "@tanstack/react-query";
import { useSelectedChild } from "../../shared/auth/SelectedChildContext";
import { ExamDdayList } from "../../shared/components/ExamDdayList";
import { ScoreSections } from "../../shared/components/ScoreSections";
import { WordScoreChart } from "../../shared/components/WordScoreChart";
import { getChildExamSchedules, getChildScores } from "./api";

/**
 * P-4. <b>단어 테스트 그래프가 화면 최상단이다.</b> 학부모가 보려는 건 자녀의 흐름이다.
 *
 * <p>자녀를 바꾸면 성적과 D-day가 함께 바뀐다 — 반이 달라지기 때문이다.
 * 등수·백분위·반 평균은 서버가 내려주지 않고 여기서도 계산하지 않는다.
 */
export default function ParentScorePage() {
  const { children, selectedStudentId, setSelectedStudentId } = useSelectedChild();

  const scores = useQuery({
    queryKey: ["parent", "scores", selectedStudentId],
    queryFn: () => getChildScores(selectedStudentId!),
    enabled: selectedStudentId !== null,
  });
  const exams = useQuery({
    queryKey: ["parent", "exam-schedules", selectedStudentId],
    queryFn: () => getChildExamSchedules(selectedStudentId!),
    enabled: selectedStudentId !== null,
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-slate-900">테스트 결과</h2>
        {children.length > 1 && (
          <select
            value={selectedStudentId ?? ""}
            onChange={(e) => setSelectedStudentId(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm"
          >
            {children.map((child) => (
              <option key={child.studentId} value={child.studentId}>
                {child.name}
              </option>
            ))}
          </select>
        )}
      </div>

      {scores.isPending || !scores.data ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : (
        <>
          <WordScoreChart points={scores.data.word.points} />
          {exams.data && <ExamDdayList schedules={exams.data} />}
          <ScoreSections internal={scores.data.internal} mock={scores.data.mock} />
        </>
      )}
    </div>
  );
}
