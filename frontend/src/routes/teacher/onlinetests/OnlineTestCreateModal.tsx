import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { errorMessage } from "../../../shared/api/errors";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { SCORE_TYPE_LABELS } from "../../../shared/score/types";
import type { ScoreType } from "../../../shared/score/types";
import { createOnlineTest, issueAnswerUploadUrl, listClassRooms } from "../api";

const NOW = new Date();
const MAX_ANSWER_BYTES = 50 * 1024 * 1024;

/**
 * T-14 출제.
 *
 * <p>정답은 "3,5,1,2,4" 형태로 한 번에 붙여넣게 한다. 25개 셀렉트를 만들면 입력이 느리고
 * 실수를 눈으로 검산할 수 없다. 길이가 문항 수와 다르면 저장 전에 화면에서 막는다 —
 * 서버도 400으로 막지만 여기서 잡아야 선생님이 어디가 틀렸는지 안다.
 *
 * <p><b>성적 반영을 켜면 과목 입력란이 나타난다.</b> scores.subject가 NOT NULL이라
 * 과목 없이는 반영 행을 만들 수 없다. 코드에서 "영어"를 채워 보내지 않는다.
 */
export default function OnlineTestCreateModal({ onClose }: { onClose: () => void }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const [classRoomId, setClassRoomId] = useState<number | "">("");
  const [title, setTitle] = useState("");
  const [questionCount, setQuestionCount] = useState("25");
  const [choiceCount, setChoiceCount] = useState("5");
  const [correctText, setCorrectText] = useState("");
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState(NOW.getMonth() + 1);
  const [week, setWeek] = useState(1);
  const [reflect, setReflect] = useState(false);
  const [scoreType, setScoreType] = useState<ScoreType>("WORD");
  const [subject, setSubject] = useState("영어");
  const [closesAt, setClosesAt] = useState("");
  const [answerS3Key, setAnswerS3Key] = useState<string | null>(null);
  const [uploadError, setUploadError] = useState<string | null>(null);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });

  const questions = Number(questionCount);
  const choices = Number(choiceCount);
  const parsed = correctText
    .split(/[\s,]+/)
    .filter((token) => token !== "")
    .map(Number);

  const lengthMismatch = parsed.length !== questions;
  const outOfRange = parsed.some(
    (value) => !Number.isInteger(value) || value < 1 || value > choices,
  );

  /**
   * 해설지는 서버를 거치지 않는다. presigned URL로 S3에 직접 PUT한다.
   *
   * <p>axios 인스턴스를 쓰지 않는다 — Authorization 헤더가 붙으면 서명이 어긋나 403이 난다.
   * Content-Type은 발급 때 보낸 값과 반드시 같아야 한다.
   */
  const upload = useMutation({
    mutationFn: async (file: File) => {
      if (file.size > MAX_ANSWER_BYTES) throw new Error("해설지는 50MB까지입니다.");
      const { uploadUrl, s3Key } = await issueAnswerUploadUrl({
        contentType: file.type,
        bytes: file.size,
      });
      const response = await fetch(uploadUrl, {
        method: "PUT",
        body: file,
        headers: { "Content-Type": file.type },
      });
      if (!response.ok) throw new Error("해설지를 올리지 못했습니다. 다시 시도해 주세요.");
      return s3Key;
    },
    onSuccess: (s3Key) => {
      setAnswerS3Key(s3Key);
      setUploadError(null);
    },
    onError: (error) => setUploadError(errorMessage(error, "해설지 업로드에 실패했습니다.")),
  });

  const save = useMutation({
    mutationFn: () =>
      createOnlineTest({
        classRoomId: Number(classRoomId),
        title: title.trim(),
        questionCount: questions,
        choiceCount: choices,
        correctChoices: parsed,
        points: null,
        answerS3Key,
        scoreType: reflect ? scoreType : null,
        subject: reflect ? subject.trim() : null,
        year,
        month,
        week,
        opensAt: null,
        closesAt: closesAt === "" ? null : new Date(closesAt).toISOString(),
      }),
    onSuccess: (created) => {
      void queryClient.invalidateQueries({ queryKey: ["teacher", "online-tests"] });
      onClose();
      navigate(`/teacher/online-tests/${created.testId}`);
    },
  });

  const canSave =
    classRoomId !== ""
    && title.trim() !== ""
    && questions > 0
    && !lengthMismatch
    && !outOfRange
    && (!reflect || subject.trim() !== "");

  function submit(event: FormEvent) {
    event.preventDefault();
    if (canSave) save.mutate();
  }

  return (
    <Modal title="온라인 테스트 출제" onClose={onClose}>
      <form onSubmit={submit} className="space-y-3">
        <select
          value={classRoomId}
          onChange={(e) => setClassRoomId(e.target.value === "" ? "" : Number(e.target.value))}
          className="w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm"
        >
          <option value="">반 선택</option>
          {(classRooms.data ?? [])
            .filter((room) => room.status === "ACTIVE")
            .map((room) => (
              <option key={room.classRoomId} value={room.classRoomId}>
                {room.name}
              </option>
            ))}
        </select>

        <input
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="테스트 이름 (예: 6월 2주차 단어시험)"
          className="w-full rounded-lg border border-slate-300 px-3 py-2 text-sm"
        />

        <div className="grid grid-cols-3 gap-2 text-sm">
          <select
            value={year}
            onChange={(e) => setYear(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          >
            {[NOW.getFullYear(), NOW.getFullYear() + 1].map((y) => (
              <option key={y} value={y}>
                {y}년
              </option>
            ))}
          </select>
          <select
            value={month}
            onChange={(e) => setMonth(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          >
            {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
              <option key={m} value={m}>
                {m}월
              </option>
            ))}
          </select>
          <select
            value={week}
            onChange={(e) => setWeek(Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          >
            {[1, 2, 3, 4, 5].map((w) => (
              <option key={w} value={w}>
                {w}주차
              </option>
            ))}
          </select>
        </div>

        <div className="grid grid-cols-2 gap-2 text-sm">
          <label className="text-slate-700">
            문항 수
            <input
              type="number"
              min={1}
              max={100}
              value={questionCount}
              onChange={(e) => setQuestionCount(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            />
          </label>
          <label className="text-slate-700">
            보기 수
            <input
              type="number"
              min={2}
              max={10}
              value={choiceCount}
              onChange={(e) => setChoiceCount(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            />
          </label>
        </div>

        <label className="block text-sm text-slate-700">
          정답 ({questions}개, 쉼표 또는 공백 구분)
          <textarea
            value={correctText}
            onChange={(e) => setCorrectText(e.target.value)}
            rows={3}
            placeholder="3,5,1,2,4, 1,3,3,5,2, ..."
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 font-mono"
          />
        </label>
        <p
          className={`text-xs ${
            lengthMismatch || outOfRange ? "text-red-600" : "text-slate-500"
          }`}
        >
          {parsed.length}개 입력됨
          {lengthMismatch && ` · 문항 수(${questions})와 다릅니다`}
          {outOfRange && ` · 1~${choices} 범위를 벗어난 값이 있습니다`}
        </p>

        <label className="block text-sm text-slate-700">
          마감 시각 (비우면 마감 없음)
          <input
            type="datetime-local"
            value={closesAt}
            onChange={(e) => setClosesAt(e.target.value)}
            className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
          />
        </label>

        <div className="space-y-1">
          <label className="block text-sm text-slate-700">
            해설지 (제출 후에만 학생에게 공개)
            <input
              type="file"
              accept="application/pdf,image/jpeg,image/png,image/webp"
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file) upload.mutate(file);
              }}
              className="mt-1 w-full text-xs"
            />
          </label>
          {upload.isPending && <p className="text-xs text-slate-500">업로드 중…</p>}
          {answerS3Key && <p className="text-xs text-emerald-700">해설지 업로드 완료</p>}
          <FormError message={uploadError} />
        </div>

        <div className="space-y-2 rounded-lg bg-slate-50 p-3">
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <input
              type="checkbox"
              checked={reflect}
              onChange={(e) => setReflect(e.target.checked)}
              className="h-4 w-4 rounded border-slate-300"
            />
            채점 결과를 성적에 반영
          </label>
          {reflect && (
            <div className="grid grid-cols-2 gap-2 text-sm">
              <select
                value={scoreType}
                onChange={(e) => setScoreType(e.target.value as ScoreType)}
                className="rounded-lg border border-slate-300 bg-white px-2 py-2"
              >
                {(Object.keys(SCORE_TYPE_LABELS) as ScoreType[]).map((type) => (
                  <option key={type} value={type}>
                    {SCORE_TYPE_LABELS[type]}
                  </option>
                ))}
              </select>
              <input
                list="onlinetest-subjects"
                value={subject}
                onChange={(e) => setSubject(e.target.value)}
                placeholder="과목 (필수)"
                className="rounded-lg border border-slate-300 px-3 py-2"
              />
              <datalist id="onlinetest-subjects">
                <option value="영어" />
                <option value="국어" />
                <option value="수학" />
              </datalist>
            </div>
          )}
          {reflect && (
            <p className="text-xs text-slate-500">
              제출 즉시 100점 환산 점수가 성적에 기록되어 학부모 그래프에 나타납니다.
            </p>
          )}
        </div>

        {save.isError && <FormError message={errorMessage(save.error)} />}

        <SubmitButton pending={save.isPending} disabled={!canSave}>
          출제 (아직 공개 안 됨)
        </SubmitButton>
      </form>
    </Modal>
  );
}
