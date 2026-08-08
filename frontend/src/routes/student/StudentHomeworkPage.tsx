import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge } from "../../shared/components/Badge";
import { gradeLabel, gradeTone } from "../../shared/homework/grade";
import { SUBMISSION_LABELS, remainingLabel } from "../../shared/homework/types";
import { listMyHomeworks } from "./api";

/**
 * S-2 숙제 목록. 미제출이면서 마감이 가까운 것이 위로 온다(서버 정렬).
 *
 * <p>남은 시간은 서버가 계산해 내려준 값이다. 기기 시계가 틀려도 같은 값이 보인다.
 */
export default function StudentHomeworkPage() {
  const homeworks = useQuery({
    queryKey: ["student", "homeworks"],
    queryFn: () => listMyHomeworks({}),
  });

  if (homeworks.isPending) {
    return <p className="text-sm text-slate-400">불러오는 중…</p>;
  }

  const items = homeworks.data?.items ?? [];

  return (
    <div className="space-y-3">
      <h2 className="text-lg font-semibold text-slate-900">숙제</h2>

      {items.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          받은 숙제가 없습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {items.map((item) => {
            // 제출 화면을 여는 유일한 근거다. 서버도 같은 기준으로 막는다 —
            // 여기서 안 그려도 URL을 직접 치면 뚫리므로 화면은 안내용일 뿐이다
            const canSubmit = item.kind === "ONLINE" || item.resubmitRequired;
            const overdue =
              item.status === "NOT_SUBMITTED" &&
              item.remainingMinutes !== null &&
              item.remainingMinutes < 0;

            return (
              <li key={item.homeworkId}>
                <Link
                  to={`/student/homeworks/${item.homeworkId}`}
                  className="block rounded-xl bg-white p-3 shadow-sm"
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="font-medium text-slate-900">{item.title}</span>
                    {item.kind === "GRID" ? (
                      <Badge tone={gradeTone(item.result)}>
                        {gradeLabel(item.result, item.completionRate, item.resolvedByResubmission)}
                      </Badge>
                    ) : item.status === "NOT_SUBMITTED" ? (
                      <Badge tone={overdue ? "danger" : "warn"}>
                        {item.remainingMinutes !== null
                          ? remainingLabel(item.remainingMinutes)
                          : SUBMISSION_LABELS[item.status]}
                      </Badge>
                    ) : (
                      <Badge tone="ok">{SUBMISSION_LABELS[item.status]}</Badge>
                    )}
                  </div>
                  <p className="mt-0.5 text-sm text-slate-500">
                    {item.classRoomName}
                    {item.lessonDate !== null && ` · ${item.lessonDate} 수업`}
                  </p>
                  {/* ONLINE의 남은 시간은 위 배지가 이미 보여준다. GRID는 재제출을 연 열에만
                      마감이 있고, 없으면 남은 시간도 없다 — 0을 보여주면 "마감 임박"으로 읽힌다 */}
                  {item.kind === "GRID" && item.remainingMinutes !== null && (
                    <p className="mt-1 text-xs text-slate-500">
                      {remainingLabel(item.remainingMinutes)}
                    </p>
                  )}
                  <div className="mt-1.5 flex flex-wrap gap-1">
                    {/* GRID는 resubmitRequired가 열려야만 다시 낼 수 있다. ONLINE의 상태는
                        위 배지가 이미 알려주므로 여기서는 GRID 재제출만 따로 짚는다 */}
                    {item.kind === "GRID" && canSubmit && (
                      <Badge tone="warn">다시 제출 필요</Badge>
                    )}
                    {item.isLate && <Badge tone="neutral">늦게 냄</Badge>}
                    {item.photoCount > 0 && <Badge tone="neutral">사진 {item.photoCount}장</Badge>}
                    {item.hasVideo && <Badge tone="neutral">영상</Badge>}
                    {item.hasFeedback && <Badge tone="ok">선생님 피드백</Badge>}
                  </div>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
