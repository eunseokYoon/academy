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
  decideClinicChangeRequest,
  decideLessonChangeRequest,
  deleteClinic,
  listClinicChangeRequests,
  listClinicReservations,
  listClinics,
  listLessonChangeRequests,
  listStudents,
  unassignClinicStudent,
  updateClinic,
} from "../api";
import type { AttendanceException, Clinic, LessonSlot } from "../api";
import { DAY_LABELS, today } from "../format";
import { RosterEditor } from "../attendance/RosterEditor";

function addDays(date: string, days: number): string {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next.toISOString().slice(0, 10);
}

function dayLabel(date: string): string {
  return DAY_LABELS[new Date(date).getDay() || 7];
}

/** "08-13 (목) A고 2학년 목요일반 19:00". 그 반에 그 요일 슬롯이 없으면 시각을 뺀다. */
function lessonLabel(lesson: LessonSlot): string {
  const time = lesson.startTime ? ` ${lesson.startTime.slice(0, 5)}` : "";
  return `${lesson.lessonDate.slice(5)} (${dayLabel(lesson.lessonDate)}) ` +
    `${lesson.classRoomName}${time}`;
}

/**
 * T-13. 정규 수업과 별개인 보충 수업 시간대를 관리한다.
 *
 * <p>변경 요청은 학생이 스스로 시간을 옮기지 못하게 하는 장치라 목록 맨 위에 둔다.
 * 대기 건이 쌓이면 학생이 계속 기다린다.
 */
export default function ClinicPage() {
  const [from, setFrom] = useState(today());
  const [to, setTo] = useState(addDays(today(), 13));
  const [creating, setCreating] = useState(false);
  const [detailOf, setDetailOf] = useState<Clinic | null>(null);

  const clinics = useQuery({
    queryKey: ["teacher", "clinics", from, to],
    queryFn: () => listClinics(from, to),
  });

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-900">클리닉 관리</h2>
        <button
          type="button"
          onClick={() => setCreating(true)}
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white"
        >
          시간대 개설
        </button>
      </div>

      <LessonChangeRequestList />
      <ChangeRequestList />

      <div className="grid grid-cols-2 gap-2 rounded-xl bg-white p-3 shadow-sm">
        <TextField
          label="시작일"
          type="date"
          value={from}
          onChange={(e) => setFrom(e.target.value)}
        />
        <TextField label="종료일" type="date" value={to} onChange={(e) => setTo(e.target.value)} />
      </div>

      {clinics.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : clinics.data?.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          이 기간에 개설된 시간대가 없습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {(clinics.data ?? []).map((clinic) => (
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
                  {clinic.capacity !== null && clinic.reservedCount >= clinic.capacity && (
                    <Badge tone="warn">정원 참</Badge>
                  )}
                  {clinic.attendanceConfirmed ? (
                    <Badge tone="ok">출석 확정</Badge>
                  ) : (
                    <Badge tone="neutral">출석 미확정</Badge>
                  )}
                </div>
                {clinic.memo && <p className="mt-1 text-xs text-slate-500">{clinic.memo}</p>}
              </button>
            </li>
          ))}
        </ul>
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
            <p className="mt-0.5 text-sm text-slate-600">{lessonLabel(request.from)}</p>
            <p className="text-sm font-medium text-slate-900">→ {lessonLabel(request.to)}</p>
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

/** 대기 중인 클리닉 변경 요청. to가 null이면 취소 요청이다. */
function ChangeRequestList() {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  const requests = useQuery({
    queryKey: ["teacher", "clinic-change-requests"],
    queryFn: () => listClinicChangeRequests("PENDING"),
  });

  const mutation = useMutation({
    mutationFn: ({ requestId, approve }: { requestId: number; approve: boolean }) =>
      decideClinicChangeRequest(requestId, approve),
    onSuccess: async () => {
      setError(null);
      await queryClient.invalidateQueries({ queryKey: ["teacher", "clinic-change-requests"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "clinics"] });
    },
    // 승인 시점에 목표 클리닉이 꽉 차 있으면 409다. 요청은 PENDING으로 남는다
    onError: (e) => setError(errorMessage(e, "처리하지 못했습니다.")),
  });

  if (!requests.data || requests.data.length === 0) return null;

  return (
    <section className="rounded-xl border border-amber-200 bg-amber-50 p-3">
      <h3 className="text-sm font-semibold text-amber-900">
        변경 요청 {requests.data.length}건 대기
      </h3>
      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}
      <ul className="mt-2 space-y-2">
        {requests.data.map((request) => (
          <li key={request.requestId} className="rounded-lg bg-white p-3">
            <p className="text-sm font-medium text-slate-900">{request.studentName}</p>
            <p className="mt-0.5 text-sm text-slate-600">
              {request.from.clinicDate} {request.from.startTime}
              {request.to
                ? ` → ${request.to.clinicDate} ${request.to.startTime}`
                : " → 취소 요청"}
            </p>
            <p className="mt-0.5 text-xs text-slate-500">
              사유: {request.reasonCode}
              {request.reasonNote && ` · ${request.reasonNote}`}
            </p>
            <div className="mt-2 flex gap-2">
              <button
                type="button"
                disabled={mutation.isPending}
                onClick={() =>
                  mutation.mutate({ requestId: request.requestId, approve: true })
                }
                className="flex-1 rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white
                           disabled:opacity-50"
              >
                승인
              </button>
              <button
                type="button"
                disabled={mutation.isPending}
                onClick={() =>
                  mutation.mutate({ requestId: request.requestId, approve: false })
                }
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
          <RosterTab clinic={clinic} students={reservations.data?.students ?? []} onClose={onClose} />
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

function RosterTab({
  clinic,
  students,
  onClose,
}: {
  clinic: Clinic;
  students: { reservationId: number; studentId: number; name: string; assignedByTeacher: boolean }[];
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
        <ul className="space-y-1">
          {students.map((student) => (
            <li
              key={student.reservationId}
              className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-2"
            >
              <span className="flex items-center gap-2 text-sm text-slate-900">
                {student.name}
                {/* 신청과 배정을 구분해야 "왜 여기 있냐"는 문의에 답할 수 있다 */}
                <Badge tone={student.assignedByTeacher ? "neutral" : "ok"}>
                  {student.assignedByTeacher ? "배정" : "신청"}
                </Badge>
              </span>
              <button
                type="button"
                onClick={() => unassign.mutate(student.studentId)}
                className="text-xs text-slate-500 underline"
              >
                해제
              </button>
            </li>
          ))}
        </ul>
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

      {adding && <AssignModal clinic={clinic} onClose={() => setAdding(false)} />}
    </div>
  );
}

function AssignModal({ clinic, onClose }: { clinic: Clinic; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [selected, setSelected] = useState<number[]>([]);
  const [keyword, setKeyword] = useState("");
  const [error, setError] = useState<string | null>(null);

  const students = useQuery({
    queryKey: ["teacher", "students", "assignable", keyword],
    queryFn: () => listStudents({ status: "ENROLLED", keyword: keyword || undefined, size: 100 }),
  });

  const mutation = useMutation({
    mutationFn: () => assignClinicStudents(clinic.clinicId, selected),
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
