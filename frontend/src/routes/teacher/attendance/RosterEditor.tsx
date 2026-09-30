import { useEffect, useState } from "react";
import { Modal } from "../../../shared/components/Modal";
import { TextField } from "../../../shared/components/TextField";
import type { AttendanceStatus } from "../../../shared/attendance/types";
import { EXCEPTION_STATUSES, STATUS_LABEL } from "../../../shared/attendance/types";
import type { AttendanceException } from "../api";

export interface RosterRow {
  studentId: number;
  name: string;
  status: AttendanceStatus;
  memo: string | null;
  /** 수업 영상 시청률. 영상이 없는 수업·클리닉이면 없다. */
  watchPercent?: number | null;
}

interface Props {
  rows: RosterRow[];
  confirmed: boolean;
  pending: boolean;
  error: string | null;
  onConfirm: (exceptions: AttendanceException[]) => void;
  /** page는 하단 고정 버튼(T-5), inline은 흐름 안 버튼(모달 안에서 쓰는 T-13). */
  variant?: "page" | "inline";
  /**
   * 선생님이 고를 수 있는 예외 상태. 기본값은 수업 출석(T-5)용이다.
   * 클리닉은 CLINIC_EXCEPTION_STATUSES를 넘긴다 — 대체 등원이 빠진다(2026-09-01 확정).
   */
  statuses?: AttendanceStatus[];
}

/**
 * T-5와 T-13이 공유하는 출석 입력. 기본값이 출석이라 <b>안 온 학생만</b> 탭한다.
 * 20명 반이면 보통 결석이 0~2명이라 20번 탭할 일을 2번으로 줄인다.
 *
 * <p>확정 전 중간 상태는 이 컴포넌트 안에만 있다. 서버에 저장하지 않는다 —
 * attendances에 미리 쓰면 캘린더의 "확정됨" 판정이 무너진다.
 */
export function RosterEditor({
  rows,
  confirmed,
  pending,
  error,
  onConfirm,
  variant = "page",
  statuses = EXCEPTION_STATUSES,
}: Props) {
  const [draft, setDraft] = useState<RosterRow[]>(rows);
  const [editing, setEditing] = useState<RosterRow | null>(null);
  const [asking, setAsking] = useState(false);

  useEffect(() => setDraft(rows), [rows]);

  const exceptions = draft.filter((row) => row.status !== "PRESENT");

  function apply(studentId: number, status: AttendanceStatus, memo: string | null) {
    setDraft((prev) =>
      prev.map((row) => (row.studentId === studentId ? { ...row, status, memo } : row)),
    );
    setEditing(null);
  }

  function submit() {
    setAsking(false);
    onConfirm(
      exceptions.map((row) => ({
        studentId: row.studentId,
        status: row.status,
        memo: row.memo?.trim() || null,
      })),
    );
  }

  return (
    <div className={`space-y-3 ${variant === "page" ? "pb-24" : ""}`}>
      <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {draft.map((row) => (
          <li key={row.studentId}>
            <button
              type="button"
              onClick={() => setEditing(row)}
              className={`w-full rounded-xl border p-3 text-left ${
                row.status === "PRESENT"
                  ? "border-emerald-200 bg-emerald-50"
                  : "border-amber-300 bg-amber-50"
              }`}
            >
              <span className="block text-sm font-medium text-slate-900">{row.name}</span>
              <span className="block text-xs text-slate-600">
                {STATUS_LABEL[row.status]}
                {/*
                  영상 시청률(2026-09-29). 선생님이 이 값을 보고 온라인을 직접 고른다 — 서버가
                  출결을 자동으로 바꾸지 않는다(2026-09-30). 기기가 보내는 값이라 판단의 참고일 뿐이다
                */}
                {row.watchPercent != null && ` · 영상 ${row.watchPercent}%`}
              </span>
              {row.memo && (
                <span className="mt-0.5 block truncate text-xs text-slate-400">{row.memo}</span>
              )}
            </button>
          </li>
        ))}
      </ul>

      {draft.length === 0 && (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          이 날짜에 재원 중인 학생이 없습니다.
        </p>
      )}

      {error && <p className="text-sm text-red-600">{error}</p>}

      {draft.length > 0 && (
        <div
          className={
            variant === "page"
              ? "fixed inset-x-0 bottom-0 border-t border-slate-200 bg-white p-3"
              : ""
          }
        >
          <div
            className={
              variant === "page" ? "mx-auto w-full max-w-screen-sm md:max-w-screen-xl" : ""
            }
          >
            <button
              type="button"
              disabled={pending}
              onClick={() => setAsking(true)}
              className="w-full rounded-lg bg-slate-900 px-4 py-3 text-sm font-medium text-white
                         disabled:opacity-50"
            >
              {/* 예외 인원 수를 버튼에 띄우면 실수를 알아챈다 */}
              {confirmed
                ? "수정 저장"
                : exceptions.length === 0
                  ? "전원 출석으로 확정"
                  : `출석 확정 (${summarize(exceptions)})`}
            </button>
          </div>
        </div>
      )}

      {editing && (
        <StatusPicker
          row={editing}
          statuses={statuses}
          onClose={() => setEditing(null)}
          onApply={(status, memo) => apply(editing.studentId, status, memo)}
        />
      )}

      {asking && (
        <Modal title="출석을 확정할까요?" onClose={() => setAsking(false)}>
          <p className="text-sm text-slate-600">
            {exceptions.length === 0
              ? `${draft.length}명 전원을 출석으로 확정합니다.`
              : `${summarize(exceptions)}으로 확정합니다. 나머지 ${
                  draft.length - exceptions.length
                }명은 출석입니다.`}
          </p>
          <p className="mt-2 text-xs text-slate-500">
            확정하면 학생·학부모 캘린더에 바로 반영됩니다. 나중에 다시 고칠 수 있습니다.
          </p>
          <div className="mt-5 flex gap-2">
            <button
              type="button"
              onClick={() => setAsking(false)}
              className="flex-1 rounded-lg border border-slate-300 px-4 py-2.5 text-sm
                         text-slate-700"
            >
              취소
            </button>
            <button
              type="button"
              onClick={submit}
              className="flex-1 rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white"
            >
              확정
            </button>
          </div>
        </Modal>
      )}
    </div>
  );
}

/**
 * 탭하면 선택지가 한 번에 뜬다. 탭할 때마다 출석→지각→결석으로 순환시키면
 * 한 번 지나쳤을 때 세 번 더 눌러야 한다.
 *
 * <p>선택지 목록은 호출부가 정한다. 수업 출석에는 「대체 등원」이 있고 클리닉에는 없다
 * (2026-09-01 확정) — 여기에 목록을 박아 두면 두 화면이 같은 버튼을 갖게 된다.
 */
function StatusPicker({
  row,
  statuses,
  onClose,
  onApply,
}: {
  row: RosterRow;
  statuses: AttendanceStatus[];
  onClose: () => void;
  onApply: (status: AttendanceStatus, memo: string | null) => void;
}) {
  const [status, setStatus] = useState<AttendanceStatus>(row.status);
  const [memo, setMemo] = useState(row.memo ?? "");

  return (
    <Modal title={row.name} onClose={onClose}>
      <div className="grid grid-cols-2 gap-2">
        <button
          type="button"
          onClick={() => setStatus("PRESENT")}
          className={`rounded-lg border px-3 py-3 text-sm ${
            status === "PRESENT"
              ? "border-slate-900 bg-slate-900 text-white"
              : "border-slate-300 text-slate-700"
          }`}
        >
          출석
        </button>
        {statuses.map((value) => (
          <button
            key={value}
            type="button"
            onClick={() => setStatus(value)}
            className={`rounded-lg border px-3 py-3 text-sm ${
              status === value
                ? "border-slate-900 bg-slate-900 text-white"
                : "border-slate-300 text-slate-700"
            }`}
          >
            {STATUS_LABEL[value]}
          </button>
        ))}
      </div>

      <div className="mt-3">
        <TextField
          label="메모"
          placeholder="학부모 사전 연락 등"
          hint="결석 사유를 남겨 두면 나중에 문의가 왔을 때 확인할 수 있습니다."
          value={memo}
          onChange={(e) => setMemo(e.target.value)}
        />
      </div>

      <button
        type="button"
        onClick={() => onApply(status, status === "PRESENT" ? null : memo.trim() || null)}
        className="mt-4 w-full rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white"
      >
        적용
      </button>
    </Modal>
  );
}

function summarize(exceptions: RosterRow[]): string {
  const counts = new Map<AttendanceStatus, number>();
  for (const row of exceptions) {
    counts.set(row.status, (counts.get(row.status) ?? 0) + 1);
  }
  return [...counts.entries()]
    .map(([status, count]) => `${STATUS_LABEL[status]} ${count}명`)
    .join(", ");
}
