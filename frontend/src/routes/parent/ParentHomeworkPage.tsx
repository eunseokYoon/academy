import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSelectedChild } from "../../shared/auth/SelectedChildContext";
import { Badge } from "../../shared/components/Badge";
import { LessonDayFilter } from "../../shared/components/LessonDayFilter";
import { PageTitle, SectionHead, TintBlock } from "../../shared/components/Section";
import { gradeLabel, gradeTone } from "../../shared/homework/grade";
import { groupByLessonDay } from "../../shared/homework/lessonDay";
import { formatDueAt } from "../../shared/homework/types";
import { ChildSelect } from "./ChildSelect";
import { getChildHomeworks } from "./api";

const NOW = new Date();

/**
 * P-3. <b>제출 여부와 채점 결과만</b> 본다.
 *
 * <p>숙제 내용·사진·선생님 피드백은 여기 오지 않는다. 응답 DTO 자체가 다르다.
 * 학생 화면(S-4) 컴포넌트를 여기서 재사용하지 마라 — 그 순간 전부 새어 나간다.
 *
 * <p>목록은 <b>수업일별</b>로 묶인다. 학생 화면(S-2)과 달리 "지금 낼 것" 덩어리가 없다 —
 * 학부모는 내는 사람이 아니라 보는 사람이라 할 일을 따로 띄울 이유가 없다.
 */
export default function ParentHomeworkPage() {
  const { selectedStudentId } = useSelectedChild();
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState<number | "">("");
  const [day, setDay] = useState<string | null | undefined>(undefined);

  /* 달을 안 골랐으면 연도만 따로 보내지 않는다. 서버는 연·월이 다 있어야 범위를 건다 */
  const params = month === "" ? {} : { year, month };
  const homeworks = useQuery({
    queryKey: ["parent", "homeworks", selectedStudentId, params],
    queryFn: () => getChildHomeworks(selectedStudentId!, params),
    enabled: selectedStudentId !== null,
  });

  /* 자녀를 바꾸면 그 아이의 수업일은 다르다. 고른 칩을 들고 가면 빈 화면이 뜬다 */
  function changeMonth(value: number | "") {
    setMonth(value);
    setDay(undefined);
  }

  function changeYear(value: number) {
    setYear(value);
    setDay(undefined);
  }

  const groups = groupByLessonDay(homeworks.data?.items ?? []);
  const shown = day === undefined ? groups : groups.filter((group) => group.lessonDate === day);

  return (
    <div className="space-y-4">
      <PageTitle action={<ChildSelect />}>숙제 제출 현황</PageTitle>

      <LessonDayFilter
        year={year}
        month={month}
        selectedDay={day}
        groups={groups}
        onYearChange={changeYear}
        onMonthChange={changeMonth}
        onDayChange={setDay}
      />

      {homeworks.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : shown.length === 0 ? (
        <TintBlock tone="neutral">
          <p className="px-4 py-6 text-center text-sm text-slate-500">
            아직 받은 숙제가 없습니다.
          </p>
        </TintBlock>
      ) : (
        shown.map((group) => (
          <section key={group.lessonDate ?? "none"}>
            <SectionHead tone="neutral" title={group.label} />
            <TintBlock tone="neutral">
              {group.items.map((item) => (
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
                  {/* 수업일은 그룹 머리로 올라갔다. 줄에는 반 이름만 남는다 */}
                  <p className="tnum mt-0.5 text-[11.5px] text-slate-500">{item.classRoomName}</p>
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
          </section>
        ))
      )}

      <p className="text-center text-xs text-slate-400">
        숙제 내용과 제출한 사진은 학생 화면에서 확인할 수 있습니다.
      </p>
    </div>
  );
}
