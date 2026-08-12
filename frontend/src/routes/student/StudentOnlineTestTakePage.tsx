import { useEffect, useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate, useParams } from "react-router-dom";
import { errorMessage } from "../../shared/api/errors";
import { Badge } from "../../shared/components/Badge";
import { FormError } from "../../shared/components/FormError";
import { BackLink } from "../../shared/components/Section";
import { remainingLabel } from "../../shared/homework/types";
import type { OnlineTestResult } from "../../shared/onlinetest/types";
import {
  getOnlineTestResult,
  getOnlineTestToTake,
  saveOnlineTestAnswers,
  submitOnlineTest,
} from "./api";

/** 답을 고를 때마다가 아니라 이 간격으로 모아 저장한다. 25문항을 연타해도 요청이 25번 나가지 않는다. */
const AUTOSAVE_DELAY_MS = 1500;

/**
 * S-10 응시. 종이 시험지를 풀고 답만 입력한다 — 문제지는 화면에 없다.
 *
 * <p>임시 저장은 <b>서버에</b> 한다. 25문항에 20~30분이 걸려서 브라우저가 닫히면
 * 처음부터 다시 해야 하기 때문이다. 출석 입력(프론트 상태만)과 반대다.
 *
 * <p>제출 전에는 정답이 응답에 없다. 결과는 제출 후 별도 응답으로 받는다.
 */
export default function StudentOnlineTestTakePage() {
  const navigate = useNavigate();
  const { testId } = useParams();
  const id = Number(testId);
  const queryClient = useQueryClient();

  const take = useQuery({
    queryKey: ["student", "online-test", id],
    queryFn: () => getOnlineTestToTake(id),
  });

  const [answers, setAnswers] = useState<(number | null)[] | null>(null);
  const [result, setResult] = useState<OnlineTestResult | null>(null);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
  const dirtyRef = useRef(false);

  const submitted = take.data?.status === "SUBMITTED";

  // 제출 완료 상태로 들어오면 결과를 바로 불러온다 (목록에서 다시 눌렀을 때)
  const savedResult = useQuery({
    queryKey: ["student", "online-test-result", id],
    queryFn: () => getOnlineTestResult(id),
    enabled: submitted && result === null,
  });

  useEffect(() => {
    if (take.data && answers === null) setAnswers(take.data.chosenChoices);
  }, [take.data, answers]);

  const save = useMutation({
    mutationFn: (next: (number | null)[]) => saveOnlineTestAnswers(id, next),
    onSuccess: () => setSavedAt(new Date()),
  });

  // 디바운스 자동 저장. 이탈 시점 저장은 cleanup에서 한 번 더 흘려보낸다
  useEffect(() => {
    if (answers === null || submitted || !dirtyRef.current) return;
    const timer = window.setTimeout(() => {
      dirtyRef.current = false;
      save.mutate(answers);
    }, AUTOSAVE_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [answers, submitted]);

  useEffect(() => {
    const flush = () => {
      if (document.visibilityState === "hidden" && dirtyRef.current && answers && !submitted) {
        dirtyRef.current = false;
        save.mutate(answers);
      }
    };
    document.addEventListener("visibilitychange", flush);
    return () => document.removeEventListener("visibilitychange", flush);
  }, [answers, submitted]);

  const submit = useMutation({
    mutationFn: async () => {
      // 마지막으로 고른 답이 저장되기 전에 제출되면 그 문항이 빠진다
      if (answers && dirtyRef.current) {
        dirtyRef.current = false;
        await saveOnlineTestAnswers(id, answers);
      }
      return submitOnlineTest(id);
    },
    onSuccess: (data) => {
      setResult(data);
      void queryClient.invalidateQueries({ queryKey: ["student", "online-tests"] });
      void queryClient.invalidateQueries({ queryKey: ["student", "scores"] });
    },
  });

  if (take.isPending) return <p className="text-sm text-slate-400">불러오는 중…</p>;
  if (take.isError || !take.data) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-slate-500">응시할 수 없는 테스트입니다.</p>
        <button
          type="button"
          onClick={() => navigate("/student/online-tests")}
          className="text-sm text-brand-700 underline"
        >
          목록으로
        </button>
      </div>
    );
  }

  const test = take.data;
  const shown = result ?? savedResult.data ?? null;
  const closed = test.closesAt != null && new Date(test.closesAt).getTime() < Date.now();
  const answeredCount = (answers ?? []).filter((a) => a != null).length;

  if (shown) return <ResultView result={shown} />;

  return (
    <div className="space-y-4">
      <BackLink onClick={() => navigate("/student/online-tests")}>테스트 목록</BackLink>

      <div className="rounded-2xl bg-white p-4 shadow-card">
        <h2 className="text-lg font-semibold text-brand-900">{test.title}</h2>
        <p className="mt-0.5 text-xs text-slate-500">
          {test.classRoomName} · {test.questionCount}문항
        </p>
        <div className="mt-2 flex flex-wrap items-center gap-2">
          <Badge>
            {answeredCount} / {test.questionCount} 입력
          </Badge>
          {test.closesAt && (
            <span className={`text-xs ${closed ? "text-red-600" : "text-slate-500"}`}>
              {remainingLabel(
                Math.round((new Date(test.closesAt).getTime() - Date.now()) / 60000),
              )}
            </span>
          )}
          {savedAt && (
            <span className="text-xs text-emerald-700">
              {savedAt.toLocaleTimeString("ko-KR")} 저장됨
            </span>
          )}
        </div>
      </div>

      {closed ? (
        <p className="rounded-xl bg-red-50 p-4 text-sm text-red-700">
          마감 시간이 지나 제출할 수 없습니다.
        </p>
      ) : (
        <ul className="space-y-2">
          {Array.from({ length: test.questionCount }, (_, index) => (
            <li key={index} className="rounded-2xl bg-white p-3 shadow-card">
              <div className="flex items-center gap-3">
                <span className="w-7 shrink-0 text-sm font-medium text-slate-500">
                  {index + 1}
                </span>
                <div className="flex flex-wrap gap-1.5">
                  {Array.from({ length: test.choiceCount }, (_, c) => c + 1).map((choice) => {
                    const picked = answers?.[index] === choice;
                    return (
                      <button
                        key={choice}
                        type="button"
                        onClick={() => {
                          dirtyRef.current = true;
                          setAnswers((prev) => {
                            const next = [...(prev ?? [])];
                            // 같은 번호를 다시 누르면 선택 해제다. 오답 확정보다 미체크가 낫다
                            next[index] = picked ? null : choice;
                            return next;
                          });
                        }}
                        className={`h-9 w-9 rounded-full border text-sm ${
                          picked
                            ? "border-brand-900 bg-brand-900 font-medium text-white"
                            : "border-slate-300 text-slate-600"
                        }`}
                      >
                        {choice}
                      </button>
                    );
                  })}
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {submit.isError && <FormError message={errorMessage(submit.error)} />}

      {!closed && (
        <div className="space-y-2">
          {answeredCount < test.questionCount && (
            <p className="text-xs text-amber-700">
              {test.questionCount - answeredCount}문항이 비어 있습니다. 미체크는 오답으로
              처리됩니다.
            </p>
          )}
          <button
            type="button"
            disabled={submit.isPending}
            onClick={() => {
              if (window.confirm("제출하면 답을 바꿀 수 없습니다. 제출하시겠습니까?")) {
                submit.mutate();
              }
            }}
            className="w-full rounded-xl bg-brand-900 px-4 py-3 text-sm font-medium text-white
                       disabled:opacity-50"
          >
            {submit.isPending ? "채점 중…" : "제출하고 채점받기"}
          </button>
        </div>
      )}
    </div>
  );
}

/** 제출 후에만 정답과 해설지가 내려온다. */
function ResultView({ result }: { result: OnlineTestResult }) {
  const navigate = useNavigate();
  // 두 칸으로 나눌 때 왼쪽이 앞 문항 전부를 갖는다. 25문항이면 왼쪽 1~13, 오른쪽 14~25
  const rowCount = Math.ceil(result.results.length / 2);

  return (
    <div className="space-y-4">
      <BackLink onClick={() => navigate("/student/online-tests")}>테스트 목록</BackLink>

      <div className="rounded-2xl bg-white p-4 text-center shadow-card">
        <p className="text-sm text-slate-500">{result.title}</p>
        <p className="mt-1 text-3xl font-bold text-brand-900">{result.score}점</p>
        <p className="mt-1 text-sm text-slate-600">
          {result.correctCount} / {result.questionCount}문항 정답
        </p>
      </div>

      {result.answerFileUrl && (
        <a
          href={result.answerFileUrl}
          target="_blank"
          rel="noreferrer"
          className="block rounded-xl border border-slate-300 bg-white px-4 py-3 text-center
                     text-sm font-medium text-brand-900"
        >
          해설지 보기
        </a>
      )}

      <section className="rounded-2xl bg-white p-4 shadow-card">
        <h3 className="mb-2 text-sm font-semibold text-brand-900">문항별 정오</h3>
        {/*
          문항 번호가 세로로 이어진다 — 1,2,3…이 왼쪽 칸을 채우고 나머지가 오른쪽으로 넘어간다.
          가로로 흐르면 눈이 1→2로 옆으로 갔다가 3에서 다시 왼쪽으로 돌아와야 해서,
          "7번이 어디 있지" 하고 찾을 때 번호를 한 줄씩 훑을 수가 없다.

          grid-flow-col만으로는 부족하고 행 수를 명시해야 한다. 안 그러면 브라우저가
          문항마다 새 열을 만들어 한 줄로 늘어선다. 인라인 style인 이유는 문항 수에 따라
          값이 달라져서다 — 좁은 화면에서는 grid-cols-1이라 이 값이 아무 영향을 주지 않는다.
        */}
        <ul
          className="grid grid-cols-1 gap-1 text-sm sm:grid-flow-col sm:grid-cols-2"
          style={{ gridTemplateRows: `repeat(${rowCount}, auto)` }}
        >
          {result.results.map((item) => (
            <li
              key={item.questionNo}
              className="flex items-center justify-between rounded-lg bg-slate-50 px-3 py-1.5"
            >
              <span className="text-slate-500">{item.questionNo}번</span>
              <span className={item.isCorrect ? "text-emerald-700" : "text-red-600"}>
                {item.isCorrect ? (
                  <>정답 {item.correct}</>
                ) : (
                  <>
                    {item.chosen ?? "미체크"} → 정답 {item.correct}
                  </>
                )}
              </span>
            </li>
          ))}
        </ul>
      </section>
    </div>
  );
}
