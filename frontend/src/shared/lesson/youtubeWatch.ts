import { useEffect, useRef } from "react";

/**
 * 수업 영상 시청 기록(2026-09-29). 서버 LessonVideoWatch 와 짝이다 — 칸 길이가 같아야 한다.
 *
 * <p><b>재생 위치가 실제로 지나간 10초 칸만 보낸다.</b> 예전(V14 이전)의 「화면을 열어 두면
 * 30초마다 봤다고 보고」가 아니다. 1초마다 플레이어의 재생 위치를 읽어 재생 중일 때만 칸을
 * 칠하므로 건너뛴 구간·멈춰 둔 시간은 안 쌓인다. 앱 youtube_player.dart 의 JS 와 같은 규칙이다.
 */
export const WATCH_BUCKET_SECONDS = 10;
const FLUSH_MS = 15_000;

interface YTPlayer {
  getPlayerState(): number;
  getCurrentTime(): number;
  getDuration(): number;
  destroy(): void;
}
interface YTNamespace {
  Player: new (
    el: HTMLIFrameElement,
    opts: { events?: { onStateChange?: (e: { data: number }) => void } },
  ) => YTPlayer;
}
declare global {
  interface Window {
    YT?: YTNamespace;
    onYouTubeIframeAPIReady?: () => void;
  }
}

let loading: Promise<YTNamespace> | null = null;

/** YouTube IFrame API 를 한 번만 싣는다. */
function loadYouTubeApi(): Promise<YTNamespace> {
  if (window.YT?.Player) return Promise.resolve(window.YT);
  if (loading) return loading;
  loading = new Promise((resolve) => {
    const previous = window.onYouTubeIframeAPIReady;
    window.onYouTubeIframeAPIReady = () => {
      previous?.();
      resolve(window.YT!);
    };
    const script = document.createElement("script");
    script.src = "https://www.youtube.com/iframe_api";
    document.head.appendChild(script);
  });
  return loading;
}

/** iframe src 에 붙인다. IFrame API 가 이 플레이어를 붙잡으려면 둘 다 있어야 한다. */
export function watchParams(): string {
  return `enablejsapi=1&origin=${encodeURIComponent(window.location.origin)}`;
}

/**
 * iframe 하나를 지켜보다 본 칸을 모아 보낸다. 15초마다·멈춤·끝·탭 숨김·화면을 떠날 때 보낸다.
 * 보내기 실패는 버린다 — 시청 기록은 곁다리다(재생을 막지 않는다).
 */
export function useYoutubeWatch(
  iframe: React.RefObject<HTMLIFrameElement>,
  embedUrl: string | null,
  send: (report: { embedUrl: string; durationSeconds: number; buckets: number[] }) => Promise<unknown>,
) {
  const sendRef = useRef(send);
  sendRef.current = send;

  useEffect(() => {
    const el = iframe.current;
    if (!el || !embedUrl) return;
    const url = embedUrl;
    let player: YTPlayer | null = null;
    let cancelled = false;
    const pending = new Set<number>();

    function flush() {
      if (!player || pending.size === 0) return;
      const duration = player.getDuration();
      if (!(duration > 0)) return;
      const buckets = [...pending];
      pending.clear();
      sendRef.current({ embedUrl: url, durationSeconds: duration, buckets }).catch(() => {});
    }

    const tick = window.setInterval(() => {
      // 1 = PLAYING. 멈춰 있거나 버퍼링 중이면 칸을 칠하지 않는다
      if (player && player.getPlayerState() === 1) {
        pending.add(Math.floor(player.getCurrentTime() / WATCH_BUCKET_SECONDS));
      }
    }, 1000);
    const periodic = window.setInterval(flush, FLUSH_MS);
    const onHide = () => {
      if (document.visibilityState === "hidden") flush();
    };
    document.addEventListener("visibilitychange", onHide);

    loadYouTubeApi().then((YT) => {
      if (cancelled) return;
      player = new YT.Player(el, {
        events: {
          // 0 = 끝, 2 = 멈춤. 그 순간까지 본 것을 바로 보낸다
          onStateChange: (e) => {
            if (e.data === 0 || e.data === 2) flush();
          },
        },
      });
    });

    return () => {
      cancelled = true;
      flush();
      window.clearInterval(tick);
      window.clearInterval(periodic);
      document.removeEventListener("visibilitychange", onHide);
    };
  }, [iframe, embedUrl]);
}
