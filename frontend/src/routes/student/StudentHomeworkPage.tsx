import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge } from "../../shared/components/Badge";
import { SUBMISSION_LABELS, formatDueAt, remainingLabel } from "../../shared/homework/types";
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
            const overdue = item.status === "NOT_SUBMITTED" && item.remainingMinutes < 0;
            return (
              <li key={item.homeworkId}>
                <Link
                  to={`/student/homeworks/${item.homeworkId}`}
                  className="block rounded-xl bg-white p-3 shadow-sm"
                >
                  <div className="flex items-start justify-between gap-2">
                    <span className="font-medium text-slate-900">{item.title}</span>
                    {item.status === "NOT_SUBMITTED" ? (
                      <Badge tone={overdue ? "danger" : "warn"}>
                        {remainingLabel(item.remainingMinutes)}
                      </Badge>
                    ) : (
                      <Badge tone="ok">{SUBMISSION_LABELS[item.status]}</Badge>
                    )}
                  </div>
                  <p className="mt-0.5 text-sm text-slate-500">
                    {item.classRoomName} · {formatDueAt(item.dueAt)} 마감
                  </p>
                  <div className="mt-1.5 flex flex-wrap gap-1">
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
