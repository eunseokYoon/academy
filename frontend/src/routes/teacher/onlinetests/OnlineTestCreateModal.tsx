import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { errorMessage } from "../../../shared/api/errors";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { createOnlineTest, listClassRooms } from "../api";
import { AnswerFilesField } from "./AnswerFilesField";
import type { AnswerFileDraft } from "./AnswerFilesField";

const NOW = new Date();

/**
 * T-14 출제.
 *
 * <p>정답은 "3,5,1,2,4" 형태로 한 번에 붙여넣게 한다. 25개 셀렉트를 만들면 입력이 느리고
 * 실수를 눈으로 검산할 수 없다. 길이가 문항 수와 다르면 저장 전에 화면에서 막는다 —
 * 서버도 400으로 막지만 여기서 잡아야 선생님이 어디가 틀렸는지 안다.
 *
 * <p><b>성적 자동 반영은 없다.</b> 이 테스트는 클리닉 테스트를 오프라인으로 못 보는
 * 학생을 위한 대체본이고, 선생님이 결과를 보고 성적 기입 탭에 직접 적는다.
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
  const [internalQuestionCount, setInternalQuestionCount] = useState("");
  const [closesAt, setClosesAt] = useState("");
  const [answerFiles, setAnswerFiles] = useState<AnswerFileDraft[]>([]);

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

  const save = useMutation({
    mutationFn: () =>
      createOnlineTest({
        classRoomId: Number(classRoomId),
        title: title.trim(),
        questionCount: questions,
        choiceCount: choices,
        correctChoices: parsed,
        points: null,
        answerS3Key: null,
        answerS3Keys: answerFiles.map((f) => f.s3Key),
        internalQuestionCount:
          internalQuestionCount.trim() === "" ? null : Number(internalQuestionCount),
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
    && !outOfRange;

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

        <AnswerFilesField files={answerFiles} onChange={setAnswerFiles} />

        {/*
          클리닉 테스트를 온라인으로 대체할 때 쓴다. 앞 N문항이 내부지문이면
          결과 화면이 내부·외부 맞힌 개수를 따로 집계해 주고, 선생님은 그 숫자를
          성적 기입 탭의 클리닉 칸에 옮겨 적기만 하면 된다.
        */}
        <div className="space-y-2 rounded-lg bg-slate-50 p-3">
          <label className="flex items-center gap-2 text-sm text-slate-700">
            <span className="shrink-0">내부지문 문항 수</span>
            <input
              type="number"
              min={0}
              max={Number.isFinite(questions) && questions > 0 ? questions : undefined}
              value={internalQuestionCount}
              onChange={(e) => setInternalQuestionCount(e.target.value)}
              placeholder="비우면 집계 안 함"
              className="w-full rounded-lg border border-slate-300 px-3 py-2"
            />
          </label>
          <p className="text-xs text-slate-500">
            앞에서부터 이 개수만큼이 내부지문입니다. 결과 화면에서 내부·외부 맞힌 개수를
            따로 보여줍니다. <b>성적에 자동 반영되지는 않습니다</b> — 선생님이 결과를 보고
            성적 기입 탭에 직접 적습니다.
          </p>
        </div>

        {save.isError && <FormError message={errorMessage(save.error)} />}

        <SubmitButton pending={save.isPending} disabled={!canSave}>
          출제 (아직 공개 안 됨)
        </SubmitButton>
      </form>
    </Modal>
  );
}
