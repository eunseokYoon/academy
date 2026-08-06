import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import {
  confirmAttendance,
  confirmClinicAttendance,
  getAttendanceRoster,
  listClinicReservations,
  listPendingAttendance,
} from "../api";
import type { AttendanceException } from "../api";
import { dayLabel } from "../../../shared/date";
import { RosterEditor } from "./RosterEditor";

/**
 * T-5. 미확정 수업·클리닉을 먼저 보여주고, 하나를 고르면 그 자리에서 출석을 확정한다.
 *
 * <p>선생님이 매일 여는 화면이라 "무엇이 남았는지"가 목록의 첫 줄이어야 한다.
 * 반·날짜를 매번 고르게 하면 확정을 빠뜨린다.
 *
 * <p>클리닉을 여기 같이 두는 이유도 같다. 클리닉 관리 화면에 들어가야만 확정할 수 있으면
 * 확정 안 된 클리닉이 조용히 쌓인다. 확정하는 곳은 두 화면이지만 <b>남은 것을 보는 곳은
 * 여기 하나</b>여야 한다.
 */
export default function AttendancePage() {
  // 수업과 클리닉은 확정 API가 달라서 선택 상태도 따로 둔다
  const [lessonId, setLessonId] = useState<number | null>(null);
  const [clinicId, setClinicId] = useState<number | null>(null);

  const pending = useQuery({
    queryKey: ["teacher", "attendance", "pending"],
    queryFn: listPendingAttendance,
  });

  if (lessonId !== null) {
    return <LessonConfirmPanel lessonId={lessonId} onBack={() => setLessonId(null)} />;
  }
  if (clinicId !== null) {
    return <ClinicConfirmPanel clinicId={clinicId} onBack={() => setClinicId(null)} />;
  }

  const lessons = pending.data?.lessons ?? [];
  const clinics = pending.data?.clinics ?? [];

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">출석 확정</h2>
        <p className="mt-1 text-sm text-slate-500">
          지난 수업·클리닉 중 아직 확정하지 않은 날입니다. 미래 일정은 여기 뜨지 않습니다.
        </p>
      </div>

      {pending.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : lessons.length === 0 && clinics.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          확정할 수업·클리닉이 없습니다.
        </p>
      ) : (
        <>
          <section>
            <h3 className="text-sm font-semibold text-slate-700">수업</h3>
            {lessons.length === 0 ? (
              <p className="mt-2 rounded-xl bg-white p-4 text-sm text-slate-500 shadow-sm">
                확정할 수업이 없습니다.
              </p>
            ) : (
              <ul className="mt-2 space-y-2">
                {lessons.map((lesson) => (
                  <li key={lesson.lessonId}>
                    <button
                      type="button"
                      onClick={() => setLessonId(lesson.lessonId)}
                      className="block w-full rounded-xl bg-white p-3 text-left shadow-sm"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium text-slate-900">
                          {lesson.lessonDate} ({dayLabel(lesson.lessonDate)})
                        </span>
                        <Badge tone="warn">미확정</Badge>
                      </div>
                      <p className="mt-0.5 text-sm text-slate-500">
                        {lesson.classRoomName} · {lesson.studentCount}명
                      </p>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>

          <section>
            <h3 className="text-sm font-semibold text-slate-700">클리닉</h3>
            {clinics.length === 0 ? (
              <p className="mt-2 rounded-xl bg-white p-4 text-sm text-slate-500 shadow-sm">
                확정할 클리닉이 없습니다.
              </p>
            ) : (
              <ul className="mt-2 space-y-2">
                {clinics.map((clinic) => (
                  <li key={clinic.clinicId}>
                    <button
                      type="button"
                      onClick={() => setClinicId(clinic.clinicId)}
                      className="block w-full rounded-xl bg-white p-3 text-left shadow-sm"
                    >
                      <div className="flex items-center justify-between gap-2">
                        <span className="font-medium text-slate-900">
                          {clinic.clinicDate} ({dayLabel(clinic.clinicDate)})
                        </span>
                        <Badge tone="warn">미확정</Badge>
                      </div>
                      {/* 클리닉엔 반 이름이 없다. 시각이 그 시간대를 가리키는 유일한 표시다 */}
                      <p className="mt-0.5 text-sm text-slate-500">
                        {clinic.startTime}~{clinic.endTime} · 신청 {clinic.studentCount}명
                      </p>
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </div>
  );
}

function BackButton({ onClick }: { onClick: () => void }) {
  return (
    <button type="button" onClick={onClick} className="text-sm text-slate-500 underline">
      ‹ 목록으로
    </button>
  );
}

function LessonConfirmPanel({ lessonId, onBack }: { lessonId: number; onBack: () => void }) {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const roster = useQuery({
    queryKey: ["teacher", "attendance", "roster", lessonId],
    queryFn: () => getAttendanceRoster(lessonId),
  });

  const mutation = useMutation({
    mutationFn: (exceptions: AttendanceException[]) => confirmAttendance(lessonId, exceptions),
    onSuccess: async (result) => {
      setError(null);
      setDone(
        `확정했습니다. 출석 ${result.summary.present} · 지각 ${result.summary.late} · ` +
          `결석 ${result.summary.absent} · 병결 ${result.summary.sick} · ` +
          `공결 ${result.summary.excused}`,
      );
      await queryClient.invalidateQueries({ queryKey: ["teacher", "attendance"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "lessons"] });
    },
    onError: (e) => {
      setDone(null);
      setError(errorMessage(e, "출석을 확정하지 못했습니다."));
    },
  });

  return (
    <div className="space-y-4">
      <BackButton onClick={onBack} />

      {roster.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : roster.data ? (
        <>
          <div>
            <h2 className="text-lg font-semibold text-slate-900">{roster.data.classRoomName}</h2>
            <p className="mt-1 text-sm text-slate-500">
              {roster.data.lessonDate} · 안 온 학생만 눌러 상태를 바꾸세요.
            </p>
          </div>

          {done && (
            <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{done}</p>
          )}

          <RosterEditor
            rows={roster.data.students}
            confirmed={roster.data.attendanceStatus === "CONFIRMED"}
            pending={mutation.isPending}
            error={error}
            onConfirm={(exceptions) => mutation.mutate(exceptions)}
          />
        </>
      ) : (
        <p className="text-sm text-red-600">수업 정보를 불러오지 못했습니다.</p>
      )}
    </div>
  );
}

/**
 * 클리닉 확정. 명단 UI는 수업과 같은 RosterEditor지만 <b>쓰는 곳이 다르다</b> —
 * attendances가 아니라 clinic_reservations.attend_status에 기록된다.
 * 클리닉 출석을 attendances에 넣지 마라. 수업 출석률이 오염된다.
 */
function ClinicConfirmPanel({ clinicId, onBack }: { clinicId: number; onBack: () => void }) {
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState<string | null>(null);

  const reservations = useQuery({
    queryKey: ["teacher", "clinic-reservations", clinicId],
    queryFn: () => listClinicReservations(clinicId),
  });

  const mutation = useMutation({
    mutationFn: (exceptions: AttendanceException[]) =>
      confirmClinicAttendance(clinicId, exceptions),
    onSuccess: async (result) => {
      setError(null);
      setDone(`확정했습니다. 출석 ${result.summary.present} · 결석 ${result.summary.absent}`);
      await queryClient.invalidateQueries({ queryKey: ["teacher", "attendance"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "clinic-reservations"] });
      await queryClient.invalidateQueries({ queryKey: ["teacher", "clinics"] });
    },
    onError: (e) => {
      setDone(null);
      setError(errorMessage(e, "출석을 확정하지 못했습니다."));
    },
  });

  return (
    <div className="space-y-4">
      <BackButton onClick={onBack} />

      {reservations.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : reservations.data ? (
        <>
          <div>
            <h2 className="text-lg font-semibold text-slate-900">
              클리닉 {reservations.data.startTime}~{reservations.data.endTime}
            </h2>
            <p className="mt-1 text-sm text-slate-500">
              {reservations.data.clinicDate} · 안 온 학생만 눌러 상태를 바꾸세요.
            </p>
          </div>

          {done && (
            <p className="rounded-lg bg-emerald-50 px-3 py-2 text-sm text-emerald-700">{done}</p>
          )}

          <RosterEditor
            rows={reservations.data.students.map((student) => ({
              studentId: student.studentId,
              name: student.name,
              // attendStatus가 null이면 아직 확정 전이다. 기본값 출석으로 열어 준다
              status: student.attendStatus ?? "PRESENT",
              memo: student.memo,
            }))}
            confirmed={reservations.data.attendanceConfirmed}
            pending={mutation.isPending}
            error={error}
            onConfirm={(exceptions) => mutation.mutate(exceptions)}
          />
        </>
      ) : (
        <p className="text-sm text-red-600">클리닉 정보를 불러오지 못했습니다.</p>
      )}
    </div>
  );
}
