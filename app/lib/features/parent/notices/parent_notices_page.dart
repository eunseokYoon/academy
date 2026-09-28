import 'package:flutter/material.dart';

import '../../../shared/notice/notice_board.dart';
import '../../../shared/notice/notice_data.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/sub_page.dart';
import '../child_gate.dart';
import '../selected_child.dart';

/// P-7 공지. **자녀 기준이다** — 반 범위 공지가 자녀마다 달라서 자녀 id 를
/// 넘긴다. 남의 자녀 id 면 서버가 403 이다.
///
/// 「학생만 보기」 공지를 거르는 건 서버가 **역할**로 한다(7-6). 앱에서 거르지 마라.
///
/// 웹 `ParentNoticePage.tsx` 처럼 제목 줄에 자녀 선택을 두지 않는다 — 홈에서
/// 고른 아이의 공지다.
class ParentNoticesPage extends StatefulWidget {
  const ParentNoticesPage({
    super.key,
    required this.controller,
    required this.selectedChild,
    required this.openUrl,
  });

  final NoticeListController controller;
  final SelectedChild selectedChild;
  final UrlOpener openUrl;

  @override
  State<ParentNoticesPage> createState() => _ParentNoticesPageState();
}

class _ParentNoticesPageState extends State<ParentNoticesPage>
    with ReappearReload<ParentNoticesPage> {
  @override
  void initState() {
    super.initState();
    _attach();
  }

  void _attach() {
    widget.controller.addListener(_onChanged);
    widget.selectedChild.addListener(_onSelection);
    widget.selectedChild.ensureLoaded();
    _load();
  }

  void _detach(NoticeListController c, SelectedChild sc) {
    c.removeListener(_onChanged);
    sc.removeListener(_onSelection);
  }

  @override
  void didUpdateWidget(covariant ParentNoticesPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller ||
        oldWidget.selectedChild != widget.selectedChild) {
      _detach(oldWidget.controller, oldWidget.selectedChild);
      _attach();
    }
  }

  @override
  void dispose() {
    _detach(widget.controller, widget.selectedChild);
    super.dispose();
  }

  void _load() {
    final id = widget.selectedChild.selectedStudentId;
    if (id != null) widget.controller.load(id);
  }

  /// **컨트롤러의 알림에서 [_load] 를 부르지 마라.** 실패하면 `_loadedAt` 이
  /// 비어 있어 다시 부르고, 또 실패해 알리고… 끝없이 돈다.
  void _onChanged() {
    if (mounted) setState(() {});
  }

  /// 자녀 목록이 도착했거나 자녀가 바뀌었다. 같은 자녀면 컨트롤러가 60초
  /// 규칙으로 거르고, 다른 자녀면 옛 목록을 버리고 새로 부른다.
  void _onSelection() {
    if (!mounted) return;
    _load();
    setState(() {});
  }

  @override
  void onReappear() => _load();

  @override
  Widget build(BuildContext context) {
    final sc = widget.selectedChild;
    final id = sc.selectedStudentId;
    return SubPageScroll(
      role: '학부모',
      onRefresh: widget.controller.refresh,
      children: [
        const PageTitle(title: '학원 공지 · 안내'),
        if (childGate(sc) case final gate?)
          gate
        else
          NoticeBoard(
            controller: widget.controller,
            studentId: id,
            openUrl: widget.openUrl,
          ),
      ],
    );
  }
}
