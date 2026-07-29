import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { confirmAttendance, getAttendanceRoster, listPendingAttendance } from "../api";
import type { AttendanceException } from "../api";
import { DAY_LABELS } from "../format";
import { RosterEditor } from "./RosterEditor";

/**
 * T-5. 미확정 수업을 먼저 보여주고, 하나를 고르면 그 자리에서 출석을 확정한다.
 *
 * <p>선생님이 매일 여는 화면이라 "무엇이 남았는지"가 목록의 첫 줄이어야 한다.
 * 반·날짜를 매번 고르게 하면 확정을 빠뜨린다.
 */
export default function AttendancePage() {
  const [lessonId, setLessonId] = useState<number | null>(null);

  const pending = useQuery({
    queryKey: ["teacher", "attendance", "pending"],
    queryFn: listPendingAttendance,
  });

  if (lessonId !== null) {
    return <ConfirmPanel lessonId={lessonId} onBack={() => setLessonId(null)} />;
  }

  return (
    <div className="space-y-4">
      <div>
        <h2 className="text-lg font-semibold text-slate-900">출석 확정</h2>
        <p className="mt-1 text-sm text-slate-500">
          지난 수업 중 아직 확정하지 않은 날입니다. 미래 수업은 여기 뜨지 않습니다.
        </p>
      </div>

      {pending.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : pending.data?.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          확정할 수업이 없습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {(pending.data ?? []).map((lesson) => (
            <li key={lesson.lessonId}>
              <button
                type="button"
                onClick={() => setLessonId(lesson.lessonId)}
                className="block w-full rounded-xl bg-white p-3 text-left shadow-sm"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="font-medium text-slate-900">
                    {lesson.lessonDate} (
                    {DAY_LABELS[new Date(lesson.lessonDate).getDay() || 7]})
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
    </div>
  );
}

function ConfirmPanel({ lessonId, onBack }: { lessonId: number; onBack: () => void }) {
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
      <button type="button" onClick={onBack} className="text-sm text-slate-500 underline">
        ‹ 목록으로
      </button>

      {roster.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : roster.data ? (
        <>
          <div>
            <h2 className="text-lg font-semibold text-slate-900">
              {roster.data.classRoomName}
            </h2>
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
