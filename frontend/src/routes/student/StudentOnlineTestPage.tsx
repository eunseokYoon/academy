import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge } from "../../shared/components/Badge";
import { remainingLabel } from "../../shared/homework/types";
import { TAKE_STATUS_LABELS } from "../../shared/onlinetest/types";
import { listMyOnlineTests } from "./api";

/** S-10 목록. 공개·재원·시작시각 조건은 서버가 이미 걸러서 내려준다. */
export default function StudentOnlineTestPage() {
  const { data, isPending } = useQuery({
    queryKey: ["student", "online-tests"],
    queryFn: listMyOnlineTests,
  });

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-900">온라인 테스트</h2>
      <p className="rounded-lg bg-slate-100 px-3 py-2 text-xs text-slate-600">
        종이 시험지를 먼저 푼 뒤 답만 입력하세요. 작성 중인 답은 자동으로 저장됩니다.
      </p>

      {isPending || !data ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : data.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          응시할 테스트가 없습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {data.map((test) => {
            const closed = test.remainingMinutes != null && test.remainingMinutes < 0;
            const submitted = test.status === "SUBMITTED";
            return (
              <li key={test.testId}>
                <Link
                  to={`/student/online-tests/${test.testId}`}
                  className="block rounded-xl bg-white p-4 shadow-sm"
                >
                  <p className="text-sm font-medium text-slate-900">{test.title}</p>
                  <p className="mt-0.5 text-xs text-slate-500">
                    {test.classRoomName} · {test.questionCount}문항
                  </p>
                  <div className="mt-2 flex flex-wrap items-center gap-1.5">
                    <Badge tone={submitted ? "ok" : closed ? "danger" : "warn"}>
                      {TAKE_STATUS_LABELS[test.status]}
                    </Badge>
                    {!submitted && test.status === "IN_PROGRESS" && (
                      <Badge>
                        {test.answeredCount} / {test.questionCount} 입력
                      </Badge>
                    )}
                    {/* 서버가 계산한 값이다. 클라이언트 시계로 다시 계산하지 않는다 */}
                    {!submitted && test.remainingMinutes != null && (
                      <span className={`text-xs ${closed ? "text-red-600" : "text-slate-500"}`}>
                        {remainingLabel(test.remainingMinutes)}
                      </span>
                    )}
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
