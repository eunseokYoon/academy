import { useState } from "react";
import { useQueries, useQuery } from "@tanstack/react-query";
import { useSelectedChild } from "../../shared/auth/SelectedChildContext";
import type { AttendanceStatus } from "../../shared/attendance/types";
import type { StudentScoreItem } from "../../shared/score/types";
import { currentWeek, weekKey } from "../../shared/score/week";
import { PageTitle, SectionHead } from "../../shared/components/Section";
import { ChildSelect } from "./ChildSelect";
import {
  getChildClinics,
  getChildHome,
  getChildLesson,
  getChildLessons,
  getChildScores,
} from "./api";
import { ReportEmpty, ReportLessonCard } from "./report/ReportLessonCard";
import { ReportLetterhead } from "./report/ReportLetterhead";
import { ReportHomeworkCard } from "./report/ReportHomeworkCard";
import type { WeekHomework } from "./report/ReportHomeworkCard";
import { ReportTestCard } from "./report/ReportTestCard";
import type { WeekTest } from "./report/ReportTestCard";

const NOW = currentWeek();
const YEARS = [NOW.year - 1, NOW.year];
const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);
const WEEKS = [1, 2, 3, 4, 5];

/**
 * P-6 주간 레포트. 한 주에 있었던 일을 <b>한 장</b>으로 보여준다 —
 * 수업(내용·중점·다음 예고) → 테스트(점수·흐름) → 숙제 순이다.
 *
 * <p>순서가 곧 그 주의 순서다. 배우고, 시험 보고, 숙제를 한다.
 * 학부모가 "이번 주 어땠어?"에 답을 얻는 데 필요한 것이 이 셋이다.
 *
 * <p><b>새 엔드포인트를 만들지 않았다.</b> 기존 넷을 조립한다 —
 * 홈(이름·반), 주차별 수업 목록, 수업 상세, 성적. 주에 수업이 한두 번이라
 * 상세 호출도 한두 번이다. 레포트 전용 API를 새로 파기 전에 이 조립이 무거워지는지
 * 먼저 재 봐라.
 *
 * <p><b>등수·백분위·반 평균·과목별 점수는 여기 없다.</b> 종이 성적표를 흉내 내다
 * 이것들을 넣지 마라 — 계산도 노출도 하지 않기로 확정된 값이고, 성적 과목은
 * 영어 하나다. 정기고사도 선생님 전용이라 이 화면에 오지 않는다.
 */
export default function ParentReportPage() {
  const { selectedStudentId } = useSelectedChild();
  const [year, setYear] = useState(NOW.year);
  const [month, setMonth] = useState(NOW.month);
  const [week, setWeek] = useState(NOW.week);

  const enabled = selectedStudentId !== null;

  // 이름·반. P-1이 이미 쓰는 키라 홈을 거쳐 온 학부모에게는 캐시가 그대로 맞는다
  const home = useQuery({
    queryKey: ["parent", "home", selectedStudentId],
    queryFn: () => getChildHome(selectedStudentId!),
    enabled,
  });

  const lessons = useQuery({
    queryKey: ["parent", "lessons", selectedStudentId, year, month, week],
    queryFn: () => getChildLessons(selectedStudentId!, { year, month, week }),
    enabled,
  });

  // 목록에는 수업 내용이 없다. 그 주 수업마다 상세를 한 번씩 부른다(보통 1~2회)
  const details = useQueries({
    queries: (lessons.data?.items ?? []).map((lesson) => ({
      queryKey: ["parent", "lesson", selectedStudentId, lesson.lessonId],
      queryFn: () => getChildLesson(selectedStudentId!, lesson.lessonId),
      enabled,
    })),
  });

  // 주차 → 날짜 변환은 서버가 한다. 화면은 고른 숫자를 그대로 넘긴다
  const clinics = useQuery({
    queryKey: ["parent", "clinics", selectedStudentId, year, month, week],
    queryFn: () => getChildClinics(selectedStudentId!, year, month, week),
    enabled,
  });

  const scores = useQuery({
    queryKey: ["parent", "scores", selectedStudentId],
    queryFn: () => getChildScores(selectedStudentId!),
    enabled,
  });

  const lessonDetails = details.flatMap((query) => (query.data ? [query.data] : []));
  const clinicList = clinics.data ?? [];
  const loading =
    lessons.isPending || clinics.isPending || details.some((query) => query.isPending);

  const homeworks: WeekHomework[] = lessonDetails.flatMap((lesson) =>
    lesson.homework
      ? [{ lessonId: lesson.lessonId, lessonDate: lesson.lessonDate, homework: lesson.homework }]
      : [],
  );

  // 선택한 주차의 값과, 그 주차까지의 시계열. 뒤 주차는 아직 안 일어난 일이라 자른다
  const selected = weekKey(year, month, week);
  const inWeek = (item: StudentScoreItem) => weekKey(item.year, item.month, item.week) === selected;
  const upTo = (item: StudentScoreItem) => weekKey(item.year, item.month, item.week) <= selected;

  const tests: WeekTest[] = (scores.data?.sections ?? []).flatMap((section) => {
    const item = section.items.find(inWeek);
    return item ? [{ section, item, history: section.items.filter(upTo) }] : [];
  });

  /**
   * 출석은 <b>수업과 클리닉을 함께 센다.</b> 그 주에 둘 다 갔으면 2회다 —
   * 학부모가 세는 것은 "아이가 학원에 간 횟수"이지 수업 출석부가 아니다.
   * 지각도 간 것이므로 포함한다. null은 아직 확정 전이라 세지 않는다.
   */
  const came = (status: AttendanceStatus | null) => status === "PRESENT" || status === "LATE";
  const attended =
    lessonDetails.filter((lesson) => came(lesson.attendanceStatus)).length
    + clinicList.filter((clinic) => came(clinic.attendStatus)).length;
  const doneHomeworks = homeworks.filter(({ homework }) =>
    homework.kind === "GRID"
      ? homework.result === "DONE"
      : homework.submissionStatus !== null && homework.submissionStatus !== "NOT_SUBMITTED",
  ).length;

  return (
    <div className="space-y-4">
      <PageTitle action={<ChildSelect />}>주간 레포트</PageTitle>

      <ReportLetterhead
        name={home.data?.student.name ?? "—"}
        classRooms={home.data?.student.classRooms ?? []}
        month={month}
        week={week}
        stats={[
          { label: "수업", value: `${lessonDetails.length}회` },
          { label: "클리닉", value: `${clinicList.length}회` },
          { label: "출석", value: `${attended}회` },
          { label: "테스트", value: `${tests.length}건` },
          { label: "숙제", value: `${doneHomeworks}/${homeworks.length}` },
        ]}
      />

      {/* 성적·클리닉 화면과 같은 년·월·주차 선택이다. 화면마다 고르는 법이 다르면 안 된다 */}
      <div className="card grid grid-cols-3 gap-2 p-3 text-sm">
        <Select value={year} onChange={setYear} options={YEARS} unit="년" />
        <Select value={month} onChange={setMonth} options={MONTHS} unit="월" />
        <Select value={week} onChange={setWeek} options={WEEKS} unit="주차" />
      </div>

      {loading ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : (
        <>
          <Section title="수업">
            {lessonDetails.length === 0 ? (
              <ReportEmpty>이 주에 공개된 수업이 없습니다.</ReportEmpty>
            ) : (
              <div className="space-y-3">
                {lessonDetails.map((lesson) => (
                  <ReportLessonCard key={lesson.lessonId} lesson={lesson} />
                ))}
              </div>
            )}
          </Section>

          <Section title="테스트 결과">
            {tests.length === 0 ? (
              <ReportEmpty>이 주에 기록된 테스트가 없습니다.</ReportEmpty>
            ) : (
              <ReportTestCard tests={tests} />
            )}
          </Section>

          <Section title="숙제">
            {homeworks.length === 0 ? (
              <ReportEmpty>이 주에 나간 숙제가 없습니다.</ReportEmpty>
            ) : (
              <ReportHomeworkCard items={homeworks} />
            )}
          </Section>
        </>
      )}
    </div>
  );
}

/**
 * 구획 제목. 주황 세로 막대를 앞에 세워 레터헤드의 헤어라인과 짝을 맞춘다 —
 * 종이 레포트에서 항목을 나누는 선과 같은 역할이다.
 */
/**
 * 이 화면에만 있던 구획 제목을 공용 SectionHead로 넘겼다. 같은 모양을 두 곳에서
 * 그리고 있으면 한쪽만 고쳐져 갈라진다 — 실제로 여기 바는 <b>전부 주황</b>이었는데,
 * 주간 레포트는 지난 주의 기록이라 "아직 안 한 것"이 아니다. 남색이 맞다.
 * 이 화면의 주황은 레터헤드의 주차 숫자와 3px 헤어라인이 맡는다.
 */
function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section>
      <SectionHead tone="brand" title={title} />
      <div className="space-y-2">{children}</div>
    </section>
  );
}

function Select({
  value,
  onChange,
  options,
  unit,
}: {
  value: number;
  onChange: (value: number) => void;
  options: number[];
  unit: string;
}) {
  return (
    <select
      value={value}
      onChange={(e) => onChange(Number(e.target.value))}
      className="rounded-lg border border-slate-300 bg-white px-2 py-2"
    >
      {options.map((option) => (
        <option key={option} value={option}>
          {option}
          {unit}
        </option>
      ))}
    </select>
  );
}
