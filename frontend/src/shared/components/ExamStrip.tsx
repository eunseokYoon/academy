import { DdayPill } from "./DdayPill";
import { EXAM_TYPE_LABELS } from "../score/types";
import type { ExamType } from "../score/types";

interface Props {
  examType: ExamType;
  startDate: string;
  /** 선생님이 아직 안 올렸으면 null이다. "미정"이라고 지어내지 마라. */
  scopeNote: string | null;
  dDay: number;
}

/**
 * 다음 시험. 인사말 <b>오른쪽</b>에 세로로 서는 칸이다.
 *
 * <p>D-day와 시험 범위는 같은 시험 이야기라 떨어뜨리지 않는다. 예전에는 알약이 카드
 * 오른쪽 위에, 범위가 화면 맨 아래 별도 카드에 있어서 D-62를 보고 범위를 알려면
 * 화면을 끝까지 내려야 했다.
 *
 * <p><b>폭이 132px로 고정이다.</b> 인사말과 나란히 서려면 좁을 수밖에 없어서 범위 글이
 * 길면 여러 줄로 흐른다 — 그래서 글자를 11px로 줄이고 줄간격을 넉넉히 뒀다.
 * 범위가 길어 읽기 어려워지면 이 칸을 인사말 아래 가로 띠로 되돌리는 게 낫다.
 *
 * <p>알약은 화면에서 가장 진한 덩어리다 — <b>한 화면에 하나만</b> 둔다.
 */
export function ExamStrip({ examType, startDate, scopeNote, dDay }: Props) {
  return (
    <div className="flex w-[132px] shrink-0 flex-col items-end gap-2 text-right">
      <DdayPill label={EXAM_TYPE_LABELS[examType]} dDay={dDay} />
      <div className="min-w-0">
        {/* 시작일은 알약이 말한 남은 날짜와 다른 정보다. 같은 숫자를 두 번 쓰는 게 아니다 */}
        <p className="tnum text-[10px] font-semibold tracking-[0.06em] text-slate-400">
          {startDate.replace(/-/g, ".")} 시작
        </p>
        {scopeNote ? (
          <p className="mt-1 whitespace-pre-wrap text-[11px] leading-relaxed text-slate-600">
            {scopeNote}
          </p>
        ) : (
          <p className="mt-1 text-[11px] leading-relaxed text-slate-400">범위 미등록</p>
        )}
      </div>
    </div>
  );
}
