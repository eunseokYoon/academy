import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { TextField } from "../../../shared/components/TextField";
import {
  assignClinicStudents,
  confirmClinicAttendance,
  createClinic,
  decideLessonChangeRequest,
  deleteClinic,
  listClinicReservations,
  listClinics,
  listLessonChangeRequests,
  listStudents,
  unassignClinicStudent,
  updateClinic,
} from "../api";
import { formatLessonSlot } from "../../../shared/lessonchange/types";
import type { AttendanceException, Clinic, ClinicReservationRow } from "../api";
import { dayLabel } from "../../../shared/date";
import { today } from "../format";
import { RosterEditor } from "../attendance/RosterEditor";

const NOW = new Date();

/**
 * T-13. 정규 수업과 별개인 보충 수업 시간대를 관리한다.
 *
 * <p><b>한 번에 한 주차만 본다</b>(2026-08-11 확정). 날짜 범위를 손으로 넣던 것을 바꿨다 —
 * 성적·온라인 테스트와 같은 년·월·주차 선택이라 화면마다 고르는 방식이 달라지지 않는다.
 * 주차 → 날짜 범위 변환은 서버(MonthWeeks)가 한다.
 *
 * <p><b>학생의 클리닉 변경 이력은 여기 없다</b>(확정). 학생이 바꾸면 즉시 반영되고
 * 학생·학부모에게 공지가 나간다. 선생님은 아래 명단에서 결과만 본다 —
 * 변경 목록을 다시 만들지 마라.
 */
export default function ClinicPage() {
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState(NOW.getMonth() + 1);
  const [week, setWeek] = useState(Math.floor((NOW.getDate() - 1) / 7) + 1);
  const [creating, setCreating] = useState(false);
  const [detailOf, setDetailOf] = useState<Clinic | null>(null);

  const clinics = useQuery({
    queryKey: ["teacher", "clinics", year, month, week],
    queryFn: () => listClinics(year, month, week),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-900">스케줄 관리</h2>
        <button
          type="button"
          onClick={() => setCreating(true)}
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white"
        >
          시간대 개설
        </button>
      </div>

      <LessonChangeRequestList />

      {/* 성적·온라인 테스트 화면과 같은 선택이다. 화면마다 다르면 매번 다시 배워야 한다 */}
      <div className="grid grid-cols-3 gap-2 rounded-xl bg-white p-3 text-sm shadow-sm">
        <select
          value={year}
          onChange={(e) => setYear(Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          aria-label="년"
        >
          {[NOW.getFullYear() - 1, NOW.getFullYear(), NOW.getFullYear() + 1].map((y) => (
            <option key={y} value={y}>{y}년</option>
          ))}
        </select>
        <select
          value={month}
          onChange={(e) => setMonth(Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          aria-label="월"
        >
          {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
            <option key={m} value={m}>{m}월</option>
          ))}
        </select>
        <select
          value={week}
          onChange={(e) => setWeek(Number(e.target.value))}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          aria-label="주차"
        >
          {[1, 2, 3, 4, 5].map((w) => (
            <option key={w} value={w}>{w}주차</option>
          ))}
        </select>
      </div>

      {clinics.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : clinics.data?.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          이 주에 개설된 시간대가 없습니다.
        </p>
      ) : (
        /*
          주차로 묶는다. 2주치가 한 줄로 늘어서면 "이번 주에 뭘 열어 뒀나"를 날짜를 읽어
          가며 세야 한다. 라벨은 서버가 준 weekLabel 그대로다 —
          여기서 날짜로 만들면 학생 화면·수업·성적이 쓰는 주차 계산과 갈라진다.
        */
        <div className="space-y-4">
          {groupByWeek(clinics.data ?? []).map(([weekLabel, weekClinics]) => (
            <div key={weekLabel}>
              <p className="eyebrow px-1">{weekLabel}</p>
              <ul className="mt-1.5 space-y-2">
                {weekClinics.map((clinic) => (
                  <li key={clinic.clinicId}>
                    <button
                      type="button"
                      onClick={() => setDetailOf(clinic)}
                      className="block w-full rounded-xl bg-white p-3 text-left shadow-sm"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium text-slate-900">
                          {clinic.clinicDate} ({dayLabel(clinic.clinicDate)}) {clinic.startTime}~
                          {clinic.endTime}
                        </span>
                        <span className="text-sm text-slate-600">
                          {clinic.reservedCount}
                          {clinic.capacity === null ? "" : `/${clinic.capacity}`}명
                        </span>
                      </div>
                      <div className="mt-2 flex flex-wrap gap-1">
                        {clinic.status === "CLOSED" && <Badge tone="neutral">마감</Badge>}
                        {clinic.capacity !== null &&
                          clinic.reservedCount >= clinic.capacity && (
                            <Badge tone="warn">정원 참</Badge>
                          )}
                        {clinic.attendanceConfirmed ? (
                          <Badge tone="ok">출석 확정</Badge>
                        ) : (
                          <Badge tone="neutral">출석 미확정</Badge>
                        )}
                      </div>
                      {clinic.memo && (
                        <p className="mt-1 text-xs text-slate-500">{clinic.memo}</p>
                      )}
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}

      {creating && <CreateClinicModal onClose={() => setCreating(false)} />}
      {detailOf && <ClinicDetailModal clinic={detailOf} onClose={() => setDetailOf(null)} />}
    </div>
  );
}

/**
 * 대기 중인 수업일 변경 요청. 클리닉 변경과 나란히 두지만 <b>동작이 전혀 다르다.</b>
 *
 * <p>승인해도 반 배정도 수업도 출석도 바뀌지 않는다. 학생·학부모에게 안내 공지가 한 건
 * 발행되는 것이 전부다. 그래서 <b>원래 반 출석부에는 그 날이 그대로 남는다</b> —
 * 출석 확정할 때 선생님이 손으로 처리해야 하고, 그 사실을 화면에도 적어 둔다.
 */
function LessonChangeRequestList() {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  const requests = useQuery({
    queryKey: ["teacher", "lesson-change-requests"],
    queryFn: () => listLessonChangeRequests("PENDING"),
  });

  const mutation = useMutation({
    mutationFn: ({ requestId, approve }: { requestId: number; approve: boolean }) =>
      decideLessonChangeRequest(requestId, approve),
    onSuccess: async () => {
      setError(null);
      await queryClient.invalidateQueries({ queryKey: ["teacher", "lesson-change-requests"] });
      // 승인은 공지를 만든다. 공지 목록을 열어 두었다면 새 글이 보여야 한다
      await queryClient.invalidateQueries({ queryKey: ["teacher", "notices"] });
    },
    onError: (e) => setError(errorMessage(e, "처리하지 못했습니다.")),
  });

  if (!requests.data || requests.data.length === 0) return null;

  return (
    <section className="rounded-xl border border-sky-200 bg-sky-50 p-3">
      <h3 className="text-sm font-semibold text-sky-900">
        수업일 변경 요청 {requests.data.length}건 대기
      </h3>
      <p className="mt-0.5 text-xs text-sky-800">
        승인하면 학생·학부모에게 안내 공지가 올라갑니다. 반 배정은 바뀌지 않으니 원래 반
        출석은 확정할 때 직접 처리해 주세요.
      </p>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      <ul className="mt-2 space-y-2">
        {requests.data.map((request) => (
          <li key={request.requestId} className="rounded-lg bg-white p-3">
            <p className="text-sm font-medium text-slate-900">{request.studentName}</p>
            <p className="mt-0.5 text-sm text-slate-600">{formatLessonSlot(request.from)}</p>
            <p className="text-sm font-medium text-slate-900">→ {formatLessonSlot(request.to)}</p>
            <p className="mt-0.5 text-xs text-slate-500">사유: {request.reason}</p>
            <div className="mt-2 flex gap-2">
              <button
                type="button"
                disabled={mutation.isPending}
                onClick={() => mutation.mutate({ requestId: request.requestId, approve: true })}
                className="flex-1 rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white
                           disabled:opacity-50"
              >
                승인
              </button>
              <button
                type="button"
                disabled={mutation.isPending}
                onClick={() => mutation.mutate({ requestId: request.requestId, approve: false })}
                className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm
                           text-slate-700 disabled:opacity-50"
              >
                거절
              </button>
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

/**
 * 주차별 묶음. 서버가 날짜 오름차순으로 주므로 순서만 유지하면 된다 —
 * 정렬을 다시 하면 서버가 정한 순서와 어긋난다. 학생 화면(S-9)과 같은 방식이다.
 */
function groupByWeek(clinics: Clinic[]): [string, Clinic[]][] {
  const groups = new Map<string, Clinic[]>();
  for (const clinic of clinics) {
    const bucket = groups.get(clinic.weekLabel);
    if (bucket) bucket.push(clinic);
    else groups.set(clinic.weekLabel, [clinic]);
  }
  return [...groups.entries()];
}


function CreateClinicModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const [clinicDate, setClinicDate] = useState(today());
  const [startTime, setStartTime] = useState("17:00");
  const [endTime, setEndTime] = useState("18:00");
  const [capacity, setCapacity] = useState("");
  const [memo, setMemo] = useState("");
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      createClinic({
        clinicDate,
        startTime,
        endTime,
        // 비워 두면 인원 제한 없음이다. 임의의 기본값을 넣지 않는다
        capacity: capacity.trim() === "" ? null : Number(capacity),
        memo: memo.trim() || null,
      }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["teacher", "clinics"] });
      onClose();
    },
    onError: (e) => setError(errorMessage(e, "시간대를 만들지 못했습니다.")),
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  return (
    <Modal title="클리닉 시간대 개설" onClose={onClose}>
      <form onSubmit={handleSubmit} className="space-y-3">
        <TextField
          label="날짜"
          type="date"
          value={clinicDate}
          onChange={(e) => setClinicDate(e.target.value)}
          required
        />
        <div className="grid grid-cols-2 gap-2">
          <TextField
            label="시작"
            type="time"
            value={startTime}
            onChange={(e) => setStartTime(e.target.value)}
            required
          />
          <TextField
            label="종료"
            type="time"
            value={endTime}
            onChange={(e) => setEndTime(e.target.value)}
            required
          />
        </div>
        <TextField
          label="정원"
          type="number"
          min={1}
          placeholder="비우면 제한 없음"
          hint="1회 정원이 정해지지 않았다면 비워 두세요."
          value={capacity}
          onChange={(e) => setCapacity(e.target.value)}
        />
        <TextField label="메모" value={memo} onChange={(e) => setMemo(e.target.value)} />
        <FormError message={error} />
        <SubmitButton pending={mutation.isPending}>개설</SubmitButton>
      </form>
    </Modal>
  );
}

type DetailTab = "roster" | "attendance";

function ClinicDetailModal({ clinic, onClose }: { clinic: Clinic; onClose: () => void }) {
  const [tab, setTab] = useState<DetailTab>("roster");

  const reservations = useQuery({
    queryKey: ["teacher", "clinic-reservations", clinic.clinicId],
    queryFn: () => listClinicReservations(clinic.clinicId),
  });

  return (
    <Modal
      title={`${clinic.clinicDate} ${clinic.startTime}~${clinic.endTime}`}
      onClose={onClose}
    >
      <div className="flex gap-1 border-b border-slate-200">
        {(
          [
            ["roster", "명단"],
            ["attendance", "출석 확정"],
          ] as [DetailTab, string][]
        ).map(([value, label]) => (
          <button
            key={value}
            type="button"
            onClick={() => setTab(value)}
            className={`border-b-2 px-3 py-2 text-sm ${
              tab === value
                ? "border-slate-900 font-medium text-slate-900"
                : "border-transparent text-slate-500"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div className="mt-3">
        {reservations.isPending ? (
          <p className="text-sm text-slate-400">불러오는 중…</p>
        ) : tab === "roster" ? (
          <RosterTab
            clinic={clinic}
            students={reservations.data?.students ?? []}
            slots={reservations.data?.slots ?? []}
            onClose={onClose}
          />
        ) : (
          <ClinicAttendanceTab
            clinic={clinic}
            students={reservations.data?.students ?? []}
            confirmed={reservations.data?.attendanceConfirmed ?? false}
          />
        )}
      </div>
    </Modal>
  );
}

/**
 * 명단. <b>도착 시각별로 묶어 그린다</b> — 5시간짜리 시간대에 20명이 한 줄로 늘어서면
 * 몇 시에 몇 명 오는지 알 수 없다. 정렬은 서버가 도착 시각 → 이름 순으로 맞춰 준다.
 */
function RosterTab({
  clinic,
  students,
  slots,
  onClose,
}: {
  clinic: Clinic;
  students: ClinicReservationRow[];
  slots: string[];
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [adding, setAdding] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ["teacher", "clinic-reservations"] });
    await queryClient.invalidateQueries({ queryKey: ["teacher", "clinics"] });
  }

  const unassign = useMutation({
    mutationFn: (studentId: number) => unassignClinicStudent(clinic.clinicId, studentId),
    onSuccess: refresh,
    onError: (e) => setError(errorMessage(e, "배정을 해제하지 못했습니다.")),
  });

  const remove = useMutation({
    mutationFn: () => deleteClinic(clinic.clinicId),
    onSuccess: async () => {
      await refresh();
      onClose();
    },
    // 신청자가 있으면 지우지 못한다. 마감(CLOSED)으로 닫아야 한다
    onError: (e) => setError(errorMessage(e, "신청자가 있어 삭제할 수 없습니다. 마감을 쓰세요.")),
  });

  const close = useMutation({
    mutationFn: () =>
      updateClinic(clinic.clinicId, {
        status: clinic.status === "OPEN" ? "CLOSED" : "OPEN",
      }),
    onSuccess: refresh,
    onError: (e) => setError(errorMessage(e, "상태를 바꾸지 못했습니다.")),
  });

  return (
    <div className="space-y-3">
      <p className="text-sm text-slate-600">
        {students.length}
        {clinic.capacity === null ? "" : `/${clinic.capacity}`}명 신청
      </p>

      {students.length === 0 ? (
        <p className="text-sm text-slate-400">아직 신청한 학생이 없습니다.</p>
      ) : (
        <div className="space-y-3">
          {groupByArrival(students).map(([arrivalTime, rows]) => (
            <div key={arrivalTime}>
              <p className="text-xs font-semibold text-slate-500">
                {arrivalTime} · {rows.length}명
              </p>
              <ul className="mt-1 space-y-1">
                {rows.map((student) => (
                  <li
                    key={student.reservationId}
                    className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2"
                  >
                    <span className="flex flex-wrap items-center gap-2 text-sm text-slate-900">
                      {student.name}
                      {/* 신청과 배정을 구분해야 "왜 여기 있냐"는 문의에 답할 수 있다 */}
                      <Badge tone={student.assignedByTeacher ? "neutral" : "ok"}>
                        {student.assignedByTeacher ? "배정" : "신청"}
                      </Badge>
                      {/* 시간대를 좁힌 뒤 남은 예약. 서버가 말없이 옮기지 않는다 */}
                      {student.outOfRange && <Badge tone="danger">시간대 밖</Badge>}
                    </span>
                    <button
                      type="button"
                      onClick={() => unassign.mutate(student.studentId)}
                      className="shrink-0 text-xs text-slate-500 underline"
                    >
                      해제
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}

      <div className="flex gap-2">
        <button
          type="button"
          onClick={() => setAdding(true)}
          className="flex-1 rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white"
        >
          학생 추가 배정
        </button>
        <button
          type="button"
          onClick={() => close.mutate()}
          className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-700"
        >
          {clinic.status === "OPEN" ? "신청 마감" : "다시 열기"}
        </button>
      </div>
      {students.length === 0 && (
        <button
          type="button"
          onClick={() => remove.mutate()}
          className="w-full text-xs text-red-600 underline"
        >
          시간대 삭제
        </button>
      )}

      {adding && (
        <AssignModal clinic={clinic} slots={slots} onClose={() => setAdding(false)} />
      )}
    </div>
  );
}

/** 도착 시각별 묶음. 서버가 이미 시각 → 이름 순으로 정렬해 보내므로 순서만 유지한다. */
function groupByArrival(students: ClinicReservationRow[]): [string, ClinicReservationRow[]][] {
  const groups = new Map<string, ClinicReservationRow[]>();
  for (const student of students) {
    const rows = groups.get(student.arrivalTime);
    if (rows) {
      rows.push(student);
    } else {
      groups.set(student.arrivalTime, [student]);
    }
  }
  return [...groups.entries()];
}

/** 고른 시각으로 <b>선택한 학생 전원</b>이 배정된다. 학생마다 다른 시각을 주는 화면이 아니다. */
function AssignModal({
  clinic,
  slots,
  onClose,
}: {
  clinic: Clinic;
  slots: string[];
  onClose: () => void;
}) {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<number[]>([]);
  const [arrivalTime, setArrivalTime] = useState(slots[0] ?? clinic.startTime);
  const [keyword, setKeyword] = useState("");
  const [error, setError] = useState<string | null>(null);

  const students = useQuery({
    queryKey: ["teacher", "students", "assignable", keyword],
    queryFn: () => listStudents({ status: "ENROLLED", keyword: keyword || undefined, size: 100 }),
  });

  const mutation = useMutation({
    mutationFn: () => assignClinicStudents(clinic.clinicId, selected, arrivalTime),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ["teacher", "clinic-reservations"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "clinics"] });
      onClose();
    },
    // 정원을 넘으면 일부만 들어가지 않고 전체가 거절된다
    onError: (e) => setError(errorMessage(e, "배정하지 못했습니다.")),
  });

  return (
    <Modal title="학생 배정" onClose={onClose}>
      <div>
        <span className="block text-sm font-medium text-slate-700">도착 시간</span>
        <div className="mt-1 flex flex-wrap gap-1">
          {slots.map((slot) => (
            <button
              key={slot}
              type="button"
              onClick={() => setArrivalTime(slot)}
              aria-pressed={arrivalTime === slot}
              className={`rounded-lg px-2.5 py-1.5 text-sm ${
                arrivalTime === slot
                  ? "bg-slate-900 font-medium text-white"
                  : "border border-slate-300 text-slate-700"
              }`}
            >
              {slot}
            </button>
          ))}
        </div>
      </div>

      <div className="mt-3" />
      <TextField
        label="이름 검색"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        placeholder="이름 일부"
      />
      <ul className="mt-3 max-h-64 space-y-1 overflow-y-auto">
        {(students.data?.items ?? []).map((student) => (
          <li key={student.studentId}>
            <label className="flex items-center gap-2 rounded-lg px-2 py-2 text-sm">
              <input
                type="checkbox"
                checked={selected.includes(student.studentId)}
                onChange={(e) =>
                  setSelected((prev) =>
                    e.target.checked
                      ? [...prev, student.studentId]
                      : prev.filter((id) => id !== student.studentId),
                  )
                }
              />
              <span className="text-slate-900">{student.name}</span>
              <span className="text-xs text-slate-400">{student.classRooms.join(", ")}</span>
            </label>
          </li>
        ))}
      </ul>
      <FormError message={error} />
      <button
        type="button"
        disabled={selected.length === 0 || mutation.isPending}
        onClick={() => mutation.mutate()}
        className="mt-3 w-full rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white
                   disabled:opacity-50"
      >
        {selected.length}명 배정
      </button>
    </Modal>
  );
}

/** 출석 확정은 T-5와 같은 UI를 그대로 쓴다. */
function ClinicAttendanceTab({
  clinic,
  students,
  confirmed,
}: {
  clinic: Clinic;
  students: {
    studentId: number;
    name: string;
    attendStatus: "PRESENT" | "LATE" | "ABSENT" | "SICK" | "EXCUSED" | null;
    memo: string | null;
  }[];
  confirmed: boolean;
}) {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: (exceptions: AttendanceException[]) =>
      confirmClinicAttendance(clinic.clinicId, exceptions),
    onSuccess: async (result) => {
      setError(null);
      setDone(`확정했습니다. 출석 ${result.summary.present} · 결석 ${result.summary.absent}`);
      await queryClient.invalidateQueries({ queryKey: ["teacher", "clinic-reservations"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "clinics"] });
    },
    onError: (e) => {
      setDone(null);
      setError(errorMessage(e, "출석을 확정하지 못했습니다."));
    },
  });

  return (
    <div>
      {done && (
        <p className="mb-2 rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{done}</p>
      )}
      <RosterEditor
        rows={students.map((student) => ({
          studentId: student.studentId,
          name: student.name,
          status: student.attendStatus ?? "PRESENT",
          memo: student.memo,
        }))}
        confirmed={confirmed}
        pending={mutation.isPending}
        error={error}
        variant="inline"
        onConfirm={(exceptions) => mutation.mutate(exceptions)}
      />
    </div>
  );
}
