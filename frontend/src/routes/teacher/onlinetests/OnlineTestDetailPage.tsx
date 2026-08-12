import { Fragment, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { FormError } from "../../../shared/components/FormError";
import { TAKE_STATUS_LABELS } from "../../../shared/onlinetest/types";
import {
  deleteOnlineTest,
  getOnlineTest,
  getOnlineTestResults,
  getOnlineTestStudentDetail,
  publishOnlineTest,
} from "../api";
import type { ClinicReflection } from "../api";

/**
 * 자동 반영 안내. <b>안 될 때 이유를 반드시 보여준다</b> — 조용히 넘어가면
 * "왜 어떤 건 성적에 자동으로 차고 어떤 건 안 차지"가 미스터리가 된다.
 */
function ClinicReflectionNotice({
  reflection,
  year,
  month,
  week,
}: {
  reflection: ClinicReflection;
  year?: number;
  month?: number;
  week?: number;
}) {
  const slot = year && month && week ? `${year}년 ${month}월 ${week}주차 ` : "";

  if (reflection === "REFLECTED") {
    return (
      <p className="rounded-lg bg-emerald-50 px-3 py-2 text-xs text-emerald-800">
        학생이 제출하면 {slot}클리닉 성적에 자동으로 들어갑니다.
        선생님이 이미 적어 둔 칸은 덮지 않습니다.
      </p>
    );
  }
  if (reflection === "NO_INTERNAL_SPLIT") {
    return (
      <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
        내부지문 문항 수가 없어 성적에 자동으로 들어가지 않습니다. 아래 숫자를 성적 기입
        탭에 직접 적어 주세요. (공개 전이면 출제 수정에서 내부지문 문항 수를 넣을 수 있습니다)
      </p>
    );
  }
  return (
    <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
      {slot}클리닉에 이미 다른 문항 수가 잡혀 있어 성적에 자동으로 들어가지 않습니다.
      반 전체가 쓰는 값이라 이 시험 하나로 덮을 수 없습니다 — 아래 숫자를 직접 적어 주세요.
    </p>
  );
}

/**
 * T-14 상세·결과. 선생님 화면이라 정답이 보인다.
 *
 * <p>공개 후에는 정답·문항 수를 고칠 수 없다 — 이미 응시한 학생의 점수가 소급 변경된다.
 * 고쳐야 하면 삭제 후 재출제이고, 제출이 하나라도 있으면 삭제도 막힌다.
 */
export default function OnlineTestDetailPage() {
  const { testId } = useParams();
  const id = Number(testId);
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const test = useQuery({
    queryKey: ["teacher", "online-test", id],
    queryFn: () => getOnlineTest(id),
  });
  const results = useQuery({
    queryKey: ["teacher", "online-test-results", id],
    queryFn: () => getOnlineTestResults(id),
  });

  /** 펼친 학생만 문항별 상세를 불러온다. 20명치를 미리 받을 이유가 없다 */
  const [openStudentId, setOpenStudentId] = useState<number | null>(null);
  const studentDetail = useQuery({
    queryKey: ["teacher", "online-test-detail", testId, openStudentId],
    queryFn: () => getOnlineTestStudentDetail(Number(testId), openStudentId!),
    enabled: openStudentId !== null,
  });

  const publish = useMutation({
    mutationFn: () => publishOnlineTest(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["teacher", "online-test", id] });
      void queryClient.invalidateQueries({ queryKey: ["teacher", "online-tests"] });
    },
  });

  const remove = useMutation({
    mutationFn: () => deleteOnlineTest(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ["teacher", "online-tests"] });
      navigate("/teacher/online-tests");
    },
  });

  if (test.isPending) return <p className="text-sm text-slate-400">불러오는 중…</p>;
  if (test.isError || !test.data) {
    return (
      <div className="space-y-3">
        <p className="text-sm text-slate-500">테스트를 찾을 수 없습니다.</p>
        <Link to="/teacher/online-tests" className="text-sm text-slate-900 underline">
          목록으로
        </Link>
      </div>
    );
  }

  const detail = test.data;
  const published = detail.publishedAt != null;

  return (
    <div className="space-y-4">
      <Link to="/teacher/online-tests" className="text-sm text-slate-500 underline">
        ← 온라인 테스트 목록
      </Link>

      <section className="space-y-2 rounded-xl bg-white p-4 shadow-sm">
        <div className="flex items-start justify-between gap-3">
          <div>
            <h2 className="text-lg font-semibold text-slate-900">{detail.title}</h2>
            <p className="mt-0.5 text-xs text-slate-500">
              {detail.classRoomName} · {detail.year}년 {detail.month}월 {detail.week}주차 ·{" "}
              {detail.questionCount}문항 / {detail.choiceCount}지선다
            </p>
          </div>
          {published ? <Badge tone="ok">공개</Badge> : <Badge tone="warn">미공개</Badge>}
        </div>

        <div className="flex flex-wrap items-center gap-1.5">
          {detail.internalQuestionCount != null ? (
            <Badge>
              내부지문 {detail.internalQuestionCount}문항 / 외부{" "}
              {detail.questionCount - detail.internalQuestionCount}문항
            </Badge>
          ) : (
            <Badge>내부·외부 집계 없음</Badge>
          )}
          {detail.closesAt && (
            <span className="text-xs text-slate-500">
              마감 {new Date(detail.closesAt).toLocaleString("ko-KR")}
            </span>
          )}
        </div>

        {detail.answerFileUrl && (
          <a
            href={detail.answerFileUrl}
            target="_blank"
            rel="noreferrer"
            className="inline-block text-sm text-slate-900 underline"
          >
            해설지 확인
          </a>
        )}

        <div className="flex gap-2 pt-2">
          {!published && (
            <button
              type="button"
              disabled={publish.isPending}
              onClick={() => publish.mutate()}
              className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white
                         disabled:opacity-50"
            >
              공개하기
            </button>
          )}
          <button
            type="button"
            disabled={remove.isPending}
            onClick={() => {
              if (window.confirm("삭제하면 되돌릴 수 없습니다. 삭제할까요?")) remove.mutate();
            }}
            className="rounded-lg border border-slate-300 px-3 py-2 text-sm text-slate-700"
          >
            삭제
          </button>
        </div>

        {publish.isError && <FormError message={errorMessage(publish.error)} />}
        {remove.isError && <FormError message={errorMessage(remove.error)} />}

        {published && (
          <p className="rounded-lg bg-slate-100 px-3 py-2 text-xs text-slate-600">
            공개된 뒤에는 정답과 문항 수를 수정할 수 없습니다. 고치려면 삭제 후 다시
            출제하세요 (제출이 있으면 삭제도 막힙니다).
          </p>
        )}
      </section>

      <section className="rounded-xl bg-white p-4 shadow-sm">
        <h3 className="mb-2 text-sm font-semibold text-slate-900">정답</h3>
        <p className="break-all font-mono text-sm text-slate-700">
          {detail.correctChoices.join(", ")}
        </p>
        {detail.points && (
          <>
            <h3 className="mb-2 mt-3 text-sm font-semibold text-slate-900">문항별 배점</h3>
            <p className="break-all font-mono text-sm text-slate-700">
              {detail.points.join(", ")}
            </p>
          </>
        )}
      </section>

      {results.data && (
        <section className="space-y-3 rounded-xl bg-white p-4 shadow-sm">
          <div className="flex items-baseline justify-between">
            <h3 className="text-sm font-semibold text-slate-900">응시 현황</h3>
            {/* 제출자만으로 계산한다. 이 값은 선생님 화면에만 있다 */}
            <span className="text-xs text-slate-500">
              제출 {results.data.counts.submitted} / {results.data.counts.total}
              {results.data.average != null && ` · 평균 ${results.data.average}점`}
            </span>
          </div>

          <ClinicReflectionNotice
            reflection={results.data.test.clinicReflection}
            year={test.data?.year}
            month={test.data?.month}
            week={test.data?.week}
          />

          <div className="overflow-x-auto">
            <table className="min-w-max text-sm">
              <thead className="bg-slate-50 text-xs text-slate-500">
                <tr>
                  <th className="px-3 py-2 text-left font-medium">학생</th>
                  <th className="px-3 py-2 text-left font-medium">상태</th>
                  <th className="px-3 py-2 text-right font-medium">점수</th>
                  {results.data.test.internalQuestionCount != null && (
                    <>
                      <th className="px-3 py-2 text-right font-medium">내부</th>
                      <th className="px-3 py-2 text-right font-medium">외부</th>
                    </>
                  )}
                  <th className="px-3 py-2 text-left font-medium">틀린 문항</th>
                  <th className="px-3 py-2" />
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100">
                {results.data.items.map((item) => {
                  const internalTotal = results.data.test.internalQuestionCount;
                  const externalTotal =
                    internalTotal == null ? null : detail.questionCount - internalTotal;
                  const open = openStudentId === item.studentId;
                  return (
                    <Fragment key={item.studentId}>
                      <tr>
                        <td className="px-3 py-2 text-slate-900">{item.name}</td>
                        <td className="px-3 py-2">
                          <Badge
                            tone={
                              item.status === "SUBMITTED"
                                ? "ok"
                                : item.status === "IN_PROGRESS"
                                  ? "warn"
                                  : "neutral"
                            }
                          >
                            {TAKE_STATUS_LABELS[item.status]}
                          </Badge>
                        </td>
                        <td className="px-3 py-2 text-right text-slate-700">
                          {item.score != null
                            ? `${item.score}점 (${item.correctCount}/${detail.questionCount})`
                            : "—"}
                        </td>
                        {internalTotal != null && (
                          <>
                            <td className="px-3 py-2 text-right text-slate-700">
                              {item.internalCorrect != null
                                ? `${item.internalCorrect}/${internalTotal}`
                                : "—"}
                            </td>
                            <td className="px-3 py-2 text-right text-slate-700">
                              {item.externalCorrect != null
                                ? `${item.externalCorrect}/${externalTotal}`
                                : "—"}
                            </td>
                          </>
                        )}
                        {/* 1부터 센 번호다. 선생님이 시험지에서 찾는 번호와 같아야 한다 */}
                        <td className="px-3 py-2 text-slate-500">
                          {item.wrongQuestionNos.length === 0
                            ? "—"
                            : item.wrongQuestionNos.join(", ")}
                        </td>
                        <td className="px-3 py-2 text-right">
                          {item.status === "SUBMITTED" && (
                            <button
                              type="button"
                              onClick={() => setOpenStudentId(open ? null : item.studentId)}
                              className="text-xs text-slate-500 underline"
                            >
                              {open ? "접기" : "문항별"}
                            </button>
                          )}
                        </td>
                      </tr>
                      {open && studentDetail.data && (
                        <tr>
                          <td colSpan={internalTotal != null ? 7 : 5} className="bg-slate-50 px-3 py-2">
                            <ul className="flex flex-wrap gap-1.5 text-xs">
                              {studentDetail.data.results.map((result) => (
                                <li
                                  key={result.questionNo}
                                  className={`rounded px-2 py-1 ${
                                    result.isCorrect
                                      ? "bg-emerald-100 text-emerald-800"
                                      : "bg-red-100 text-red-800"
                                  }`}
                                >
                                  {result.questionNo}번 · 선택 {result.chosen ?? "—"} / 정답{" "}
                                  {result.correct}
                                </li>
                              ))}
                            </ul>
                          </td>
                        </tr>
                      )}
                    </Fragment>
                  );
                })}
              </tbody>
            </table>
          </div>
        </section>
      )}
    </div>
  );
}
