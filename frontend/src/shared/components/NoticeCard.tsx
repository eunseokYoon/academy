import { Link } from "react-router-dom";
import { SectionHead, TintBlock } from "./Section";
import type { NoticeSummary } from "../notice/api";

interface Props {
  totalCount: number;
  recent: NoticeSummary[];
  /** 전체 목록 경로. 역할마다 다르다 — /student/notices · /parent/notices */
  to: string;
}

/**
 * 홈의 공지 배너. 학생(S-1)·학부모(P-1)가 <b>같은 것을 본다</b> —
 * 두 화면이 어긋나면 "엄마 폰에는 다르게 나온다"는 문의가 된다.
 *
 * <p><b>노랑에서 남색으로 바꿨다(2026-08-12).</b> 예전 주석은 "홈에서 유일하게 따뜻한
 * 색이라 이 카드 하나가 튀어서 새 글 신호가 된다"였는데, 그 전제가 사라졌다 —
 * 이제 미완료 숙제 구획이 주황 면이라, 노란 공지가 그 옆에 서면 <b>둘 다 경고로</b>
 * 읽히고 정작 급한 쪽이 안 드러난다. 공지는 급한 게 아니라 읽을 것이다.
 * 색 규칙은 Section.tsx의 SectionTone 주석이 정본이다.
 *
 * <p>읽음 표시는 만들지 않는다(notice_reads는 범위 밖). 그래서 알약의 숫자는
 * "안 읽은 수"가 아니라 전체 건수다 — 줄어들지 않는 게 정상이다.
 */
export function NoticeCard({ totalCount, recent, to }: Props) {
  return (
    <section>
      <SectionHead tone="brand" title="학원 공지" count={totalCount} to={to} />

      {recent.length === 0 ? (
        <TintBlock tone="brand">
          <p className="px-4 py-5 text-center text-sm text-brand-700/70">
            등록된 공지가 없습니다.
          </p>
        </TintBlock>
      ) : (
        <TintBlock tone="brand">
          {recent.map((notice) => (
            <Link
              key={notice.noticeId}
              to={to}
              className="flex items-center gap-2 px-3.5 py-2.5 transition-colors
                         active:bg-brand-100/60"
            >
              {/*
                고정은 옅은 배지가 아니라 채운 남색이다. 옅게 두면 남색 면 위에서
                배경과 붙어 "고정"인지 아닌지가 안 읽힌다.
              */}
              {notice.pinned && (
                <span className="shrink-0 rounded bg-brand-600 px-1.5 py-0.5 text-[9.5px]
                                 font-bold text-white">
                  고정
                </span>
              )}
              <span className="min-w-0 flex-1 truncate text-[13px] text-brand-950">
                {notice.title}
              </span>
              <span className="tnum shrink-0 text-[11px] text-brand-600/70">
                {notice.publishedAt.slice(5, 10).replace("-", "/")}
              </span>
            </Link>
          ))}
        </TintBlock>
      )}
    </section>
  );
}
