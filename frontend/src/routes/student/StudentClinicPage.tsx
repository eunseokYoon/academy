import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorCode, errorMessage } from "../../shared/api/errors";
import { Badge } from "../../shared/components/Badge";
import { Modal } from "../../shared/components/Modal";
import { PageTitle, SectionHead, TintBlock } from "../../shared/components/Section";
import { TextAreaField } from "../../shared/components/TextAreaField";
import { formatClinicSlot } from "../../shared/clinic/types";
import {
  changeClinicReservation,
  listLessonChangeCandidates,
  listMyChangeableLessons,
  listMyClinics,
  listMyLessonChanges,
  requestLessonChange,
  listClinicSeries,
  reserveClinic,
  reserveClinicSeries,
} from "./api";
import { formatLessonSlot } from "../../shared/lessonchange/types";
import type { SeriesReserveResult, StudentClinic } from "./api";

function todayString(): string {
  return new Date().toISOString().slice(0, 10);
}

function addDays(date: string, days: number): string {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next.toISOString().slice(0, 10);
}

/**
 * S-9 클리닉 신청.
 *
 * <p>화면에 다른 학생 이름이 없다. 서버도 인원 수만 내려준다.
 * 마감 여부(full)는 서버가 계산한 값을 그대로 쓴다 — capacity가 null일 수 있어서
 * 프론트에서 reservedCount >= capacity를 계산하면 깨진다.
 *
 * <p><b>도착 시각 목록(slots)도 서버가 준다.</b> 시작·종료로 여기서 다시 만들지 마라 —
 * "마지막 슬롯은 종료 1시간 전" 규칙이 두 곳으로 갈라지면 학생이 고른 시각을 서버가 거절한다.
 *
 * <p>변경에 선생님 승인이 없다(2026-08-10 확정). 대신 사유가 필수고, 변경하면
 * <b>공지가 한 건 발행되어</b> 본인과 학부모의 공지 탭에 뜬다(수업일 변경과 같은 경로).
 * 이 화면에 변경 이력을 따로 그리지 마라 — 같은 내용이 두 곳에 있으면 어느 쪽이 최신인지 헷갈린다.
 *
 * <p><b>취소 버튼을 만들지 마라</b>(2026-08-10 확정). 못 가면 다른 시각으로 옮기고,
 * 아예 빠져야 하면 선생님이 T-13에서 배정을 해제한다.
 */
export default function StudentClinicPage() {
  const from = todayString();
  const to = addDays(from, 20);
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);
  const [changing, setChanging] = useState<StudentClinic | null>(null);
  // 클리닉별로 고른 도착 시각. 안 고르면 첫 슬롯이다
  const [picked, setPicked] = useState<Record<number, string>>({});

  const clinics = useQuery({
    queryKey: ["student", "clinics", from, to],
    queryFn: () => listMyClinics(from, to),
  });

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ["student", "clinics"] });
    // 변경하면 공지가 한 건 발행된다. 공지 탭이 최신이 되도록 같이 비운다
    await queryClient.invalidateQueries({ queryKey: ["notices"] });
  }

  const reserve = useMutation({
    mutationFn: (clinic: StudentClinic) =>
      reserveClinic(clinic.clinicId, picked[clinic.clinicId] ?? clinic.slots[0]),
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



  const mine = (clinics.data ?? []).filter((clinic) => clinic.myReservation !== null);
  const openClinics = clinics.data ?? [];

  return (
    <div className="space-y-5">
      <PageTitle>스케줄 관리</PageTitle>
      {error && <p className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>}

      <LessonChangeSection />

      <ClinicSeriesSection />

      <section>
        <SectionHead tone="brand" title="내 클리닉" count={mine.length} />
        {mine.length === 0 ? (
          <TintBlock tone="neutral">
            <p className="px-4 py-5 text-center text-sm text-slate-500">
              신청한 클리닉이 없습니다.
            </p>
          </TintBlock>
        ) : (
          <ul className="space-y-2">
            {mine.map((clinic) => (
              <li key={clinic.clinicId} className="rounded-2xl bg-white p-3.5 shadow-card">
                <div className="flex items-center justify-between gap-2">
                  <div className="min-w-0">
                    {/* 도착 시각이 주인공이다. 시간대(17:00~22:00)는 그 아래 작게 둔다 —
                        학생이 기억해야 하는 건 "몇 시에 가는가"다 */}
                    <p className="text-base font-semibold text-brand-900">
                      {clinic.clinicDate.slice(5)} {clinic.myReservation!.arrivalTime} 도착
                    </p>
                    <p className="text-xs text-slate-500">{formatClinicSlot(clinic)}</p>
                  </div>
                  <Badge tone="ok">신청 완료</Badge>
                </div>
                {/* 취소 버튼은 없다(2026-08-10 확정). 못 가면 다른 시각으로 옮긴다 —
                    학생이 스스로 명단에서 사라지면 선생님이 그날 인원을 신뢰할 수 없다 */}
                <button
                  type="button"
                  onClick={() => setChanging(clinic)}
                  className="mt-2 w-full rounded-lg border border-slate-300 px-3 py-2 text-sm
                             text-slate-700"
                >
                  시간 변경
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      <section>
        <SectionHead tone="neutral" title="신청 가능한 시간" />
        {clinics.isPending ? (
          <p className="mt-2 text-sm text-slate-400">불러오는 중…</p>
        ) : openClinics.length === 0 ? (
          <TintBlock tone="neutral">
            <p className="px-4 py-5 text-center text-sm text-slate-500">
              지금 신청할 수 있는 시간이 없습니다.
            </p>
          </TintBlock>
        ) : (
          /*
            주차로 묶는다. 3주치가 한 줄로 늘어서면 "이번 주에 갈 수 있는 게 뭔지"를
            날짜를 읽어 가며 세야 한다. 라벨은 서버가 준 weekLabel 그대로다 —
            여기서 날짜로 만들면 수업·성적이 쓰는 주차 계산과 갈라진다.
          */
          <div className="mt-2 space-y-4">
            {groupByWeek(openClinics).map(([weekLabel, weekClinics]) => (
              <div key={weekLabel}>
                <p className="eyebrow px-1">{weekLabel}</p>
                <ul className="mt-1.5 space-y-2">
                  {weekClinics.map((clinic) => (
                    <li key={clinic.clinicId} className="rounded-2xl bg-white p-3 shadow-card">
                      <div className="flex items-center justify-between gap-2">
                        <div>
                          <p className="text-sm font-medium text-brand-900">
                            {formatClinicSlot(clinic)}
                          </p>
                          <p className="text-xs text-slate-500">
                            {clinic.reservedCount}
                            {clinic.capacity === null ? "" : `/${clinic.capacity}`}명
                          </p>
                        </div>
                        {clinic.myReservation ? (
                          <span className="text-xs text-slate-400">
                            {clinic.myReservation.arrivalTime} 신청함
                          </span>
                        ) : clinic.full ? (
                          <span className="text-xs text-slate-400">마감</span>
                        ) : (
                          <button
                            type="button"
                            disabled={reserve.isPending || clinic.slots.length === 0}
                            onClick={() => reserve.mutate(clinic)}
                            className="rounded-lg bg-brand-900 px-3 py-2 text-sm font-medium
                                       text-white disabled:opacity-50"
                          >
                            신청
                          </button>
                        )}
                      </div>

                      {/* 몇 시에 올지 먼저 고르고 신청한다. 안 고르면 첫 슬롯이다 */}
                      {!clinic.myReservation && !clinic.full && (
                        <SlotPicker
                          slots={clinic.slots}
                          value={picked[clinic.clinicId] ?? clinic.slots[0]}
                          onChange={(slot) =>
                            setPicked((prev) => ({ ...prev, [clinic.clinicId]: slot }))
                          }
                        />
                      )}
                    </li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        )}
      </section>

      {changing && (
        <ChangeModal
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

/**
 * 주차별 묶음. 서버가 날짜 오름차순으로 주므로 순서만 유지하면 된다 —
 * 정렬을 다시 하면 서버가 정한 순서와 어긋난다.
 */
function groupByWeek(clinics: StudentClinic[]): [string, StudentClinic[]][] {
  const groups = new Map<string, StudentClinic[]>();
  for (const clinic of clinics) {
    const bucket = groups.get(clinic.weekLabel);
    if (bucket) bucket.push(clinic);
    else groups.set(clinic.weekLabel, [clinic]);
  }
  return [...groups.entries()];
}

/** 도착 시각 고르기. 슬롯 수가 대여섯 개라 드롭다운보다 버튼이 빠르다. */
function SlotPicker({
  slots,
  value,
  onChange,
}: {
  slots: string[];
  value: string | undefined;
  onChange: (slot: string) => void;
}) {
  if (slots.length === 0) {
    return <p className="mt-2 text-xs text-slate-400">고를 수 있는 시간이 없습니다.</p>;
  }
  return (
    <div className="mt-2 flex flex-wrap gap-1">
      {slots.map((slot) => (
        <button
          key={slot}
          type="button"
          onClick={() => onChange(slot)}
          aria-pressed={value === slot}
          className={`rounded-lg px-2.5 py-1.5 text-sm ${
            value === slot
              ? "bg-brand-900 font-medium text-white"
              : "border border-slate-300 text-slate-700"
          }`}
        >
          {slot}
        </button>
      ))}
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
        <SectionHead tone="neutral" title="수업일 변경" />
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="rounded-lg bg-brand-900 px-3 py-2 text-sm font-medium text-white"
        >
          수업 변경
        </button>
      </div>

      {requests.data && requests.data.length > 0 ? (
        <ul className="mt-2 space-y-2">
          {requests.data.map((request) => (
            <li key={request.requestId} className="rounded-2xl bg-white p-3 shadow-card">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0 text-sm">
                  <p className="text-slate-500">{formatLessonSlot(request.from)}</p>
                  <p className="font-medium text-brand-900">→ {formatLessonSlot(request.to)}</p>
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
        <p className="mt-2 rounded-2xl bg-white p-4 text-sm text-slate-500 shadow-card">
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
        className="mt-4 w-full rounded-lg bg-brand-900 px-4 py-2.5 text-sm font-medium text-white
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
 * 시간 변경 · 다른 클리닉으로 이동. <b>선생님 승인이 없다</b>(2026-08-10 확정) —
 * 저장하면 즉시 바뀐다.
 *
 * <p>사유가 필수인 이유가 있다. 승인 단계가 없어서 <b>이 문장이 선생님에게 남는 유일한
 * 설명</b>이다. 선택 입력으로 바꾸지 마라.
 */
function ChangeModal({
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
  const [arrivalTime, setArrivalTime] = useState(clinic.myReservation!.arrivalTime);
  const [reason, setReason] = useState("");
  const [error, setError] = useState<string | null>(null);

  // 옮길 클리닉이 바뀌면 슬롯 목록도 바뀐다. 이전 선택이 새 목록에 없으면 첫 슬롯으로 되돌린다
  const target =
    targetClinicId === ""
      ? clinic
      : (candidates.find((c) => c.clinicId === Number(targetClinicId)) ?? clinic);
  const slots = target.slots;

  const mutation = useMutation({
    mutationFn: () =>
      changeClinicReservation(clinic.clinicId, {
        targetClinicId: targetClinicId === "" ? null : Number(targetClinicId),
        arrivalTime: slots.includes(arrivalTime) ? arrivalTime : slots[0],
        reason: reason.trim(),
      }),
    onSuccess: async () => {
      await onDone();
      onClose();
    },
    onError: (e) => setError(errorMessage(e, "변경하지 못했습니다.")),
  });

  return (
    <Modal title="시간 변경" onClose={onClose}>
      <p className="text-sm text-slate-600">
        현재 {formatClinicSlot(clinic)} · {clinic.myReservation!.arrivalTime} 도착
      </p>

      <label className="mt-3 block">
        <span className="block text-sm font-medium text-slate-700">클리닉</span>
        <select
          value={targetClinicId}
          onChange={(e) => setTargetClinicId(e.target.value)}
          className="mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-base"
        >
          <option value="">그대로 (시간만 변경)</option>
          {candidates.map((candidate) => (
            <option key={candidate.clinicId} value={candidate.clinicId}>
              {formatClinicSlot(candidate)}
            </option>
          ))}
        </select>
      </label>

      <div className="mt-3">
        <span className="block text-sm font-medium text-slate-700">도착 시간</span>
        <SlotPicker
          slots={slots}
          value={slots.includes(arrivalTime) ? arrivalTime : slots[0]}
          onChange={setArrivalTime}
        />
      </div>

      <div className="mt-3">
        <TextAreaField
          label="사유 (필수)"
          rows={3}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
        />
      </div>

      {error && <p className="mt-2 text-sm text-red-600">{error}</p>}

      <button
        type="button"
        disabled={reason.trim() === "" || slots.length === 0 || mutation.isPending}
        onClick={() => mutation.mutate()}
        className="mt-4 w-full rounded-lg bg-brand-900 px-4 py-2.5 text-sm font-medium text-white
                   disabled:opacity-50"
      >
        변경하기
      </button>
      <p className="mt-2 text-xs text-slate-500">
        바로 반영됩니다. 적으신 사유는 선생님과 학부모님께 그대로 전달됩니다.
      </p>
    </Modal>
  );
}

/**
 * 시리즈 일괄 신청. 「매주 화요일 17:00~22:00 · 총 18회」를 카드 하나로 보여주고
 * 도착 시각만 고르면 남은 회차를 한 번에 신청한다.
 *
 * <p><b>시리즈에는 id가 없다.</b> 서버가 오늘 이후 OPEN 클리닉을 (요일·시작·종료)로
 * 묶은 결과라, 신청할 때 그 세 값을 그대로 되돌려 보낸다.
 *
 * <p>정원이 찬 회차는 서버가 건너뛰고 사유를 돌려준다 — 전체가 실패하지 않는다.
 * 못 가는 날은 신청 후 기존 변경 흐름(사유 적고 옮기기)으로 처리한다.
 */
function ClinicSeriesSection() {
  const queryClient = useQueryClient();
  const [picked, setPicked] = useState<Record<string, string>>({});
  const [result, setResult] = useState<SeriesReserveResult | null>(null);

  const series = useQuery({
    queryKey: ["student", "clinic-series"],
    queryFn: listClinicSeries,
  });

  const reserve = useMutation({
    mutationFn: reserveClinicSeries,
    onSuccess: (data) => {
      setResult(data);
      void queryClient.invalidateQueries({ queryKey: ["student", "clinic-series"] });
      void queryClient.invalidateQueries({ queryKey: ["student", "clinics"] });
    },
  });

  const items = series.data ?? [];
  if (items.length === 0) {
    return null;
  }

  return (
    <section>
      <SectionHead tone="brand" title="정기 클리닉 일괄 신청" count={items.length} />
      <ul className="space-y-2">
        {items.map((item) => {
          const key = `${item.dayOfWeek}-${item.startTime}-${item.endTime}`;
          const remaining = item.totalCount - item.reservedCount;
          const arrivalTime = picked[key] ?? item.slots[0];
          return (
            <li key={key} className="rounded-2xl bg-white p-3.5 shadow-card">
              <p className="font-medium text-slate-900">
                매주 {DAY_LABELS[item.dayOfWeek]}요일 · {item.startTime}~{item.endTime}
              </p>
              <p className="mt-0.5 text-xs text-slate-500">
                {item.firstDate.slice(5)} ~ {item.lastDate.slice(5)} · 총 {item.totalCount}회
                {item.reservedCount > 0 && ` (${item.reservedCount}회 신청함)`}
              </p>
              {remaining === 0 ? (
                <p className="mt-2 text-sm text-slate-500">전부 신청했습니다.</p>
              ) : (
                <div className="mt-2 flex items-center gap-2">
                  <select
                    value={arrivalTime}
                    onChange={(e) => setPicked((prev) => ({ ...prev, [key]: e.target.value }))}
                    className="rounded-lg border border-slate-300 px-2 py-1.5 text-sm"
                  >
                    {item.slots.map((slot) => (
                      <option key={slot} value={slot}>
                        {slot} 도착
                      </option>
                    ))}
                  </select>
                  <button
                    type="button"
                    disabled={reserve.isPending}
                    onClick={() =>
                      reserve.mutate({
                        dayOfWeek: item.dayOfWeek,
                        startTime: item.startTime,
                        endTime: item.endTime,
                        arrivalTime,
                      })
                    }
                    className="rounded-lg bg-brand-900 px-3 py-1.5 text-sm font-medium text-white
                               disabled:opacity-50"
                  >
                    {reserve.isPending ? "신청 중…" : `${remaining}회 신청하기`}
                  </button>
                </div>
              )}
            </li>
          );
        })}
      </ul>

      {/* 건너뛴 회차를 사후 통보한다. 정원이 있는 경우에만 생긴다 */}
      {result && (
        <TintBlock tone="neutral">
          <div className="px-4 py-3 text-sm">
            <p className="text-slate-900">{result.reserved}회 신청되었습니다.</p>
            {result.skipped > 0 && (
              <p className="mt-1 text-slate-500">
                {result.skipped}회는 빠졌습니다 —{" "}
                {result.skippedItems
                  .map((item) => `${item.clinicDate.slice(5)} (${SKIP_REASONS[item.reason] ?? item.reason})`)
                  .join(", ")}
              </p>
            )}
          </div>
        </TintBlock>
      )}
    </section>
  );
}

const DAY_LABELS: Record<number, string> = {
  1: "월", 2: "화", 3: "수", 4: "목", 5: "금", 6: "토", 7: "일",
};

const SKIP_REASONS: Record<string, string> = {
  ALREADY: "이미 신청함",
  CAPACITY: "정원 초과",
  NO_SLOT: "그날은 시간이 다름",
};

