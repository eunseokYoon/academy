import 'package:flutter/material.dart';
import 'package:video_player/video_player.dart';

import '../../../core/theme/app_colors.dart';
import '../../../shared/widgets/play_button.dart';

/// 제출한 영상 재생. 웹 `<video controls playsInline preload="metadata">` 자리다
/// (2026-09-26 확정 — 앱에서도 재생한다).
///
/// **트랜스코딩을 하지 않는다**(CLAUDE.md 6번). 그래서 아이폰 원본(HEVC)은
/// 일부 안드로이드 기기에서 재생이 안 될 수 있다 — 그때는 재생 실패를 알리고
/// 끝낸다. 올린 파일 자체는 멀쩡하고 선생님은 T-7 에서 본다.
///
/// `url` 은 서명된 GET 주소라 만료된다. 상세를 다시 부르면 새 주소가 오고,
/// 주소가 바뀌면 플레이어를 새로 만든다.
class SubmissionVideoPlayer extends StatefulWidget {
  const SubmissionVideoPlayer({super.key, required this.url});

  final String url;

  @override
  State<SubmissionVideoPlayer> createState() => _SubmissionVideoPlayerState();
}

class _SubmissionVideoPlayerState extends State<SubmissionVideoPlayer> {
  VideoPlayerController? _controller;
  bool _failed = false;

  @override
  void initState() {
    super.initState();
    _open(widget.url);
  }

  @override
  void didUpdateWidget(covariant SubmissionVideoPlayer oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.url != widget.url) {
      _controller?.dispose();
      _open(widget.url);
    }
  }

  void _open(String url) {
    final controller = VideoPlayerController.networkUrl(Uri.parse(url));
    _controller = controller;
    _failed = false;
    controller.addListener(_onTick);
    controller.initialize().then(
      (_) {
        if (mounted && identical(controller, _controller)) setState(() {});
      },
      onError: (Object _) {
        if (mounted && identical(controller, _controller)) {
          setState(() => _failed = true);
        }
      },
    );
  }

  void _onTick() {
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _controller?.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final controller = _controller;
    if (_failed || controller == null) {
      return const _Frame(
        child: Text(
          '이 기기에서 재생할 수 없는 영상입니다.\n올린 영상은 선생님이 볼 수 있습니다.',
          textAlign: TextAlign.center,
          style: TextStyle(fontSize: 12, color: AppColors.slate300),
        ),
      );
    }
    final value = controller.value;
    if (!value.isInitialized) {
      return const _Frame(
        child: SizedBox.square(
          dimension: 24,
          child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
        ),
      );
    }
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: ColoredBox(
        color: Colors.black,
        child: AspectRatio(
          aspectRatio: value.aspectRatio,
          child: Stack(
            fit: StackFit.expand,
            children: [
              VideoPlayer(controller),
              // 화면 전체가 재생·일시정지 버튼이다.
              Material(
                type: MaterialType.transparency,
                child: InkWell(
                  key: const Key('video-toggle'),
                  onTap: () =>
                      value.isPlaying ? controller.pause() : controller.play(),
                  child: value.isPlaying
                      ? const SizedBox.expand()
                      : Center(
                          child: Semantics(
                            label: '재생',
                            child: const PlayButton(),
                          ),
                        ),
                ),
              ),
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: VideoProgressIndicator(
                  controller,
                  allowScrubbing: true,
                  colors: const VideoProgressColors(
                    playedColor: Colors.white,
                    bufferedColor: AppColors.slate400,
                    backgroundColor: AppColors.slate600,
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 재생 전·실패 때의 검은 16:9 틀. 자리를 미리 잡아야 로드되면서 아래가 안 튄다.
class _Frame extends StatelessWidget {
  const _Frame({required this.child});

  final Widget child;

  @override
  Widget build(BuildContext context) => ClipRRect(
    borderRadius: BorderRadius.circular(8),
    child: ColoredBox(
      color: Colors.black,
      child: AspectRatio(
        aspectRatio: 16 / 9,
        child: Center(
          child: Padding(padding: const EdgeInsets.all(16), child: child),
        ),
      ),
    ),
  );
}
