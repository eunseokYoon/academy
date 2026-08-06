import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorCode, errorMessage } from "../../shared/api/errors";
import { Badge } from "../../shared/components/Badge";
import { Modal } from "../../shared/components/Modal";
import { TextAreaField } from "../../shared/components/TextAreaField";
import { formatClinicSlot } from "../../shared/clinic/types";
import {
  cancelClinicReservation,
  listLessonChangeCandidates,
  listMyChangeableLessons,
  listMyClinics,
  listMyLessonChanges,
  requestClinicChange,
  requestLessonChange,
  reserveClinic,
} from "./api";
import { formatLessonSlot } from "../../shared/lessonchange/types";
import type { StudentClinic } from "./api";

function todayString(): string {
  return new Date().toISOString().slice(0, 10);
}

function addDays(date: string, days: number): string {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next.toISOString().slice(0, 10);
}

function formatClinicSlot(clinic: { clinicDate: string; startTime: string; endTime: string }): string {
  const day = dayLabel(clinic.clinicDate);
  return `${clinic.clinicDate.slice(5)} (${day}) ${clinic.startTime}~${clinic.endTime}`;
}

/**
 * S-9 클리닉 신청.
 *
 * <p>화면에 다른 학생 이름이 없다. 서버도 인원 수만 내려준다.
 * 마감 여부(full)는 서버가 계산한 값을 그대로 쓴다 — capacity가 null일 수 있어서
 * 프론트에서 reservedCount >= capacity를 계산하면 깨진다.
 */
export default function StudentClinicPage() {
  const from = todayString();
  const to = addDays(from, 20);
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [changing, setChanging] = useState<StudentClinic | null>(null);

  const clinics = useQuery({
    queryKey: ["student", "clinics", from, to],
    queryFn: () => listMyClinics(from, to),
  });

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ["student", "clinics"] });
  }

  const reserve = useMutation({
    mutationFn: (clinicId: number) => reserveClinic(clinicId),
    onSuccess: async () => {
      setError(null);
      await refresh();
    },
    // 정원 초과와 중복 신청은 다른 코드다. 문구를 다르게 보여준다
    onError: (e) =>
      setError(
        errorCode(e) === "CLINIC_CAPACITY_EXCEEDED"
          ? "정원이 모두 찼습니다. 다른 시간을 골라 주세요."
          : errorCode(e) === "DUPLICATE_RESOURCE"
            ? "이미 신청하셨거나 신청이 마감된 시간입니다."
            : errorMessage(e, "신청하지 못했습니다."),
      ),
  });

  const cancel = useMutation({
    mutationFn: (clinicId: number) => cancelClinicReservation(clinicId),
    onSuccess: async () => {
      setError(null);
      await refresh();
    },
    onError: (e) => setError(errorMessage(e, "취소하지 못했습니다.")),
  });

  const mine = (clinics.data ?? []).filter((clinic) => clinic.myReservation !== null);

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-900">스케줄 변경</h2>
      {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

      <LessonChangeSection />

      <h3 className="pt-2 text-base font-semibold text-slate-900">클리닉 신청</h3>

      <section>
        <h3 className="text-sm font-semibold text-slate-700">내 클리닉</h3>
        {mine.length === 0 ? (
          <p className="mt-2 rounded-xl bg-white p-4 text-sm text-slate-500 shadow-sm">
            신청한 클리닉이 없습니다.
          </p>
        ) : (
          <ul className="mt-2 space-y-2">
            {mine.map((clinic) => (
              <li key={clinic.clinicId} className="rounded-xl bg-white p-3 shadow-sm">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-medium text-slate-900">{formatClinicSlot(clinic)}</span>
                  {clinic.myReservation?.changeRequestStatus === "PENDING" ? (
                    <Badge tone="warn">변경 요청 중</Badge>
                  ) : (
                    <Badge tone="ok">신청 완료</Badge>
                  )}
                </div>
                {clinic.myReservation?.changeRequestStatus !== "PENDING" && (
                  <div className="mt-2 flex gap-2">
                    <button
                      type="button"
                      onClick={() => cancel.mutate(clinic.clinicId)}
                      className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm
                                 text-slate-700"
                    >
                      취소
                    </button>
                    <button
                      type="button"
                      onClick={() => setChanging(clinic)}
                      className="flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm
                                 text-slate-700"
                    >
                      시간 변경 요청
                    </button>
                  </div>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <h3 className="text-sm font-semibold text-slate-700">신청 가능한 시간</h3>
        {clinics.isPending ? (
          <p className="mt-2 text-sm text-slate-400">불러오는 중…</p>
        ) : (
          <ul className="mt-2 space-y-2">
            {(clinics.data ?? []).map((clinic) => (
              <li
                key={clinic.clinicId}
                className="flex items-center justify-between gap-2 rounded-xl bg-white p-3
                           shadow-sm"
              >
                <div>
                  <p className="text-sm font-medium text-slate-900">{formatClinicSlot(clinic)}</p>
                  <p className="text-xs text-slate-500">
                    {clinic.reservedCount}
                    {clinic.capacity === null ? "" : `/${clinic.capacity}`}명
                  </p>
                </div>
                {clinic.myReservation ? (
                  <span className="text-xs text-slate-400">신청함</span>
                ) : clinic.full ? (
                  <span className="text-xs text-slate-400">마감</span>
                ) : (
                  <button
                    type="button"
                    disabled={reserve.isPending}
                    onClick={() => reserve.mutate(clinic.clinicId)}
                    className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white
                               disabled:opacity-50"
                  >
                    신청
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </section>

      {changing && (
        <ChangeRequestModal
          clinic={changing}
          candidates={(clinics.data ?? []).filter(
            (candidate) =>
              candidate.clinicId !== changing.clinicId &&
              !candidate.full &&
              candidate.myReservation === null,
          )}
          onClose={() => setChanging(null)}
          onDone={refresh}
        />
      )}
    </div>
  );
}

const STATUS_LABELS: Record<string, string> = {
  PENDING: "승인 대기",
  APPROVED: "승인됨",
  REJECTED: "거절됨",
};

/**
 * 수업일 변경. 클리닉과 별개이고 <b>예약이 아니다.</b>
 *
 * <p>승인되면 학생·학부모에게 공지가 한 건 뜨는 것이 전부다. 반 배정도 수업도 바뀌지
 * 않고 원래 반 출석부에는 그 날이 그대로 남는다. 그래서 "변경됨"이 아니라 "안내가 갔다"에
 * 가깝고, 화면에서도 그렇게 읽히도록 안내 문구를 붙였다.
 */
function LessonChangeSection() {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);

  const requests = useQuery({
    queryKey: ["student", "lesson-changes"],
    queryFn: listMyLessonChanges,
  });

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ["student", "lesson-changes"] });
  }

  return (
    <section>
      <div className="flex items-center justify-between gap-2">
        <h3 className="text-sm font-semibold text-slate-700">수업일 변경</h3>
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white"
        >
          수업 변경
        </button>
      </div>

      {requests.data && requests.data.length > 0 ? (
        <ul className="mt-2 space-y-2">
          {requests.data.map((request) => (
            <li key={request.requestId} className="rounded-xl bg-white p-3 shadow-sm">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0 text-sm">
                  <p className="text-slate-500">{formatLessonSlot(request.from)}</p>
                  <p className="font-medium text-slate-900">→ {formatLessonSlot(request.to)}</p>
                </div>
                <Badge
                  tone={
                    request.status === "APPROVED"
                      ? "ok"
                      : request.status === "REJECTED"
                        ? "danger"
                        : "warn"
                  }
                >
                  {STATUS_LABELS[request.status]}
                </Badge>
              </div>
              <p className="mt-1 text-xs text-slate-500">사유 · {request.reason}</p>
            </li>
          ))}
        </ul>
      ) : (
        <p className="mt-2 rounded-xl bg-white p-4 text-sm text-slate-500 shadow-sm">
          변경 요청한 수업이 없습니다.
        </p>
      )}

      {open && <LessonChangeModal onClose={() => setOpen(false)} onDone={refresh} />}
    </section>
  );
}

/**
 * 못 가는 내 수업을 먼저 고르면 <b>그 수업이 있는 주(월~일)</b>의 다른 반 수업이 후보로 뜬다.
 * 후보는 학생이 고를 때마다 서버에 다시 물어본다 — 주가 바뀌면 후보도 통째로 바뀌기 때문이다.
 */
function LessonChangeModal({
  onClose,
  onDone,
}: {
  onClose: () => void;
  onDone: () => Promise<void>;
}) {
  const [fromLessonId, setFromLessonId] = useState<string>("");
  const [toLessonId, setToLessonId] = useState<string>("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  const myLessons = useQuery({
    queryKey: ["student", "lesson-changes", "my-lessons"],
    queryFn: listMyChangeableLessons,
  });

  const candidates = useQuery({
    queryKey: ["student", "lesson-changes", "candidates", fromLessonId],
    queryFn: () => listLessonChangeCandidates(Number(fromLessonId)),
    enabled: fromLessonId !== "",
  });

  const mutation = useMutation({
    mutationFn: () =>
      requestLessonChange({
        fromLessonId: Number(fromLessonId),
        toLessonId: Number(toLessonId),
        reason: reason.trim(),
      }),
    onSuccess: async () => {
      await onDone();
      onClose();
    },
    onError: (e) =>
      setError(
        errorCode(e) === "DUPLICATE_RESOURCE"
          ? "이미 그 수업에 변경 요청을 보냈습니다."
          : errorMessage(e, "요청을 보내지 못했습니다."),
      ),
  });

  return (
    <Modal title="수업일 변경 요청" onClose={onClose}>
      <label className="block">
        <span className="block text-sm font-medium text-slate-700">못 가는 수업</span>
        <select
          value={fromLessonId}
          onChange={(e) => {
            setFromLessonId(e.target.value);
            // 주가 바뀌면 후보 목록이 통째로 달라진다. 이전 선택을 남기면
            // 화면에 없는 수업이 그대로 제출된다
            setToLessonId("");
          }}
          className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-base"
        >
          <option value="">선택하세요</option>
          {(myLessons.data ?? []).map((lesson) => (
            <option key={lesson.lessonId} value={lesson.lessonId}>
              {formatLessonSlot(lesson)}
            </option>
          ))}
        </select>
      </label>
      {myLessons.data && myLessons.data.length === 0 && (
        <p className="mt-1 text-xs text-slate-500">앞으로 한 달 안에 예정된 수업이 없습니다.</p>
      )}

      <label className="mt-3 block">
        <span className="block text-sm font-medium text-slate-700">대신 갈 수업</span>
        <select
          value={toLessonId}
          onChange={(e) => setToLessonId(e.target.value)}
          disabled={fromLessonId === ""}
          className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-base
                     disabled:bg-slate-100"
        >
          <option value="">
            {fromLessonId === "" ? "못 가는 수업을 먼저 고르세요" : "선택하세요"}
          </option>
          {(candidates.data ?? []).map((lesson) => (
            <option key={lesson.lessonId} value={lesson.lessonId}>
              {formatLessonSlot(lesson)}
            </option>
          ))}
        </select>
      </label>
      {fromLessonId !== "" && candidates.data && candidates.data.length === 0 && (
        <p className="mt-1 text-xs text-slate-500">그 주에 갈 수 있는 다른 반 수업이 없습니다.</p>
      )}

      {/*
        사유가 곧 공지 본문이다. 선택지를 두지 않은 것은 옵션 목록이 미확정이라
        값을 지어내지 않기 위해서다. 확정되면 select로 바꾼다.
      */}
      <div className="mt-3">
        <TextAreaField
          label="변경 사유 (필수)"
          rows={3}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </div>

      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}

      <button
        type="button"
        disabled={
          fromLessonId === "" || toLessonId === "" || reason.trim() === "" || mutation.isPending
        }
        onClick={() => mutation.mutate()}
        className="mt-4 w-full rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white
                   disabled:opacity-50"
      >
        요청 보내기
      </button>
      <p className="mt-2 text-xs text-slate-500">
        선생님이 승인하면 학생·학부모에게 변경 안내 공지가 올라갑니다. 반 배정이 바뀌는 것은
        아니라서, 원래 반 출석은 선생님이 따로 처리합니다.
      </p>
    </Modal>
  );
}

/**
 * 시간 변경은 요청만 하고 선생님이 승인한다. 학생이 직접 옮기지 못한다.
 * 사유 선택은 필수라 버튼을 누르면 이 다이얼로그가 먼저 뜬다.
 */
function ChangeRequestModal({
  clinic,
  candidates,
  onClose,
  onDone,
}: {
  clinic: StudentClinic;
  candidates: StudentClinic[];
  onClose: () => void;
  onDone: () => Promise<void>;
}) {
  const [targetClinicId, setTargetClinicId] = useState<string>("");
  const [reasonCode, setReasonCode] = useState("");
  const [reasonNote, setReasonNote] = useState("");
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      requestClinicChange({
        reservationId: clinic.myReservation!.reservationId,
        targetClinicId: targetClinicId === "" ? null : Number(targetClinicId),
        reasonCode: reasonCode.trim(),
        reasonNote: reasonNote.trim() || null,
      }),
    onSuccess: async () => {
      await onDone();
      onClose();
    },
    onError: (e) => setError(errorMessage(e, "요청을 보내지 못했습니다.")),
  });

  return (
    <Modal title="시간 변경 요청" onClose={onClose}>
      <p className="text-sm text-slate-600">현재 {formatClinicSlot(clinic)}</p>

      <label className="mt-3 block">
        <span className="block text-sm font-medium text-slate-700">옮길 시간</span>
        <select
          value={targetClinicId}
          onChange={(e) => setTargetClinicId(e.target.value)}
          className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-base"
        >
          <option value="">취소 요청 (다른 시간 없이 취소)</option>
          {candidates.map((candidate) => (
            <option key={candidate.clinicId} value={candidate.clinicId}>
              {formatClinicSlot(candidate)}
            </option>
          ))}
        </select>
      </label>

      {/*
        사유 옵션 목록이 아직 확정되지 않았다. 임의로 만들어 두면 나중에 값이
        어긋나므로 지금은 자유 입력으로 받고, 확정되면 select로 바꾼다.
      */}
      <label className="mt-3 block">
        <span className="block text-sm font-medium text-slate-700">사유 (필수)</span>
        <input
          value={reasonCode}
          onChange={(e) => setReasonCode(e.target.value)}
          required
          placeholder="예: 학교 일정"
          className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 text-base"
        />
      </label>

      <div className="mt-3">
        <TextAreaField
          label="자세한 사정"
          rows={3}
          value={reasonNote}
          onChange={(e) => setReasonNote(e.target.value)}
        />
      </div>

      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}

      <button
        type="button"
        disabled={reasonCode.trim() === "" || mutation.isPending}
        onClick={() => mutation.mutate()}
        className="mt-4 w-full rounded-lg bg-slate-900 px-4 py-2.5 text-sm font-medium text-white
                   disabled:opacity-50"
      >
        요청 보내기
      </button>
      <p className="mt-2 text-xs text-slate-500">
        선생님이 승인해야 시간이 바뀝니다. 승인 전까지는 원래 시간에 오세요.
      </p>
    </Modal>
  );
}
