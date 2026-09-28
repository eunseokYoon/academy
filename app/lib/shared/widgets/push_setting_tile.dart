import 'package:flutter/material.dart';

import '../../core/api/api_exception.dart';
import '../../core/push/push_setting.dart';
import '../../core/theme/app_colors.dart';

import 'package:academy_app/shared/lib/param_controller.dart';

import 'app_card.dart';
import 'form_error.dart';

/// 알림 받기 스위치. 학생 「내 정보 · 성적」(S-7)과 학부모 내 정보(P-5)에 있다 —
/// 개인정보처리방침의 국외 이전 「거부 방법」이 이 스위치를 가리킨다. 지우지 마라.
///
/// 화면 본문과 따로 받는다. 본문을 못 받았어도 이 줄은 그린다
/// ([AccountFooter] 와 같은 이유 — 끌 곳이 사라지면 안 된다).
///
/// 이 스위치는 **서버 발송**을 끈다. 기기 설정의 알림 권한과는 별개다.
class PushSettingTile extends StatefulWidget {
  const PushSettingTile({super.key, required this.controller});

  final PushSettingController controller;

  @override
  State<PushSettingTile> createState() => _PushSettingTileState();
}

class _PushSettingTileState extends State<PushSettingTile> {
  /// 바꾸는 중에 보여 줄 값. 저장 뒤 다시 받기 전까지 옛 값으로 튀지 않게 한다.
  bool? _pending;
  String? _error;

  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onChanged);
    widget.controller.load();
  }

  @override
  void didUpdateWidget(covariant PushSettingTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
      widget.controller.load();
    }
  }

  @override
  void dispose() {
    widget.controller.removeListener(_onChanged);
    super.dispose();
  }

  void _onChanged() {
    if (mounted) setState(() {});
  }

  Future<void> _change(bool enabled) async {
    setState(() {
      _pending = enabled;
      _error = null;
    });
    try {
      await widget.controller.repository.change(enabled);
      await widget.controller.reload();
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.message);
    } catch (_) {
      if (mounted) setState(() => _error = '알림 설정을 바꾸지 못했습니다.');
    } finally {
      if (mounted) setState(() => _pending = null);
    }
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.controller;
    final value = _pending ?? c.data;
    final failed = value == null && c.status == HomeStatus.error;
    final error = _error ?? (failed ? c.error : null);
    return AppCard(
      padding: const EdgeInsets.fromLTRB(16, 12, 12, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            children: [
              const Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '알림 받기',
                      style: TextStyle(
                        fontSize: 15,
                        fontWeight: FontWeight.w600,
                        color: AppColors.brand950,
                      ),
                    ),
                    SizedBox(height: 2),
                    Text(
                      '공지·숙제·성적·출결 알림',
                      style: TextStyle(fontSize: 13, color: AppColors.slate500),
                    ),
                  ],
                ),
              ),
              if (failed)
                TextButton(
                  key: const Key('push-setting-retry'),
                  onPressed: c.refresh,
                  child: const Text('다시 시도'),
                )
              else
                Switch(
                  key: const Key('push-setting-switch'),
                  value: value ?? false,
                  activeTrackColor: AppColors.brand600,
                  // 받는 중·바꾸는 중에는 누를 수 없다.
                  onChanged: value == null || _pending != null ? null : _change,
                ),
            ],
          ),
          if (error != null) ...[
            const SizedBox(height: 8),
            FormError(key: const Key('push-setting-error'), message: error),
          ],
        ],
      ),
    );
  }
}
