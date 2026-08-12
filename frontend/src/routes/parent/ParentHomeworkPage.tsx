import { useQuery } from "@tanstack/react-query";
import { useSelectedChild } from "../../shared/auth/SelectedChildContext";
import { Badge } from "../../shared/components/Badge";
import { PageTitle, TintBlock } from "../../shared/components/Section";
import { gradeLabel, gradeTone } from "../../shared/homework/grade";
import { formatDueAt } from "../../shared/homework/types";
import { ChildSelect } from "./ChildSelect";
import { getChildHomeworks } from "./api";

/**
 * P-3. <b>제출 여부와 채점 결과만</b> 본다.
 *
 * <p>숙제 내용·사진·선생님 피드백은 여기 오지 않는다. 응답 DTO 자체가 다르다.
 * 학생 화면(S-4) 컴포넌트를 여기서 재사용하지 마라 — 그 순간 전부 새어 나간다.
 */
export default function ParentHomeworkPage() {
  const { selectedStudentId } = useSelectedChild();

  const homeworks = useQuery({
    queryKey: ["parent", "homeworks", selectedStudentId],
    queryFn: () => getChildHomeworks(selectedStudentId!, {}),
    enabled: selectedStudentId !== null,
  });

  const items = homeworks.data?.items ?? [];

  return (
    <div className="space-y-4">
      <PageTitle action={<ChildSelect />}>숙제 제출 현황</PageTitle>

      {homeworks.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : items.length === 0 ? (
        <TintBlock tone="neutral">
          <p className="px-4 py-6 text-center text-sm text-slate-500">
            아직 받은 숙제가 없습니다.
          </p>
        </TintBlock>
      ) : (
        <TintBlock tone="neutral">
          {items.map((item) => (
            <div key={item.homeworkId} className="px-3.5 py-3.5">
              <div className="flex items-start justify-between gap-2">
                <span className="min-w-0 flex-1 truncate text-[15px] font-bold
                                 tracking-[-0.015em] text-brand-900">
                  {item.title}
                </span>
                {item.kind === "GRID" ? (
                  <Badge tone={gradeTone(item.result)}>
                    {gradeLabel(item.result, item.completionRate, item.resolvedByResubmission)}
                  </Badge>
                ) : item.status === "NOT_SUBMITTED" ? (
                  <Badge tone="danger">미제출</Badge>
                ) : (
                  <Badge tone="ok">제출</Badge>
                )}
              </div>
              <p className="tnum mt-0.5 text-[11.5px] text-slate-500">
                {item.classRoomName}
                {item.lessonDate !== null && ` · ${item.lessonDate} 수업`}
              </p>
              {/* GRID는 재제출을 열기 전까지 마감이 없다 */}
              {item.dueAt !== null && (
                <p className="tnum mt-0.5 text-[11.5px] text-slate-500">
                  {item.kind === "GRID" ? "다시 제출 마감" : "마감"} {formatDueAt(item.dueAt)}
                </p>
              )}
              {item.isLate && (
                <div className="mt-1.5">
                  <Badge tone="warn">늦게 냄</Badge>
                </div>
              )}
            </div>
          ))}
        </TintBlock>
      )}

      <p className="text-center text-xs text-slate-400">
        숙제 내용과 제출한 사진은 학생 화면에서 확인할 수 있습니다.
      </p>
    </div>
  );
}
