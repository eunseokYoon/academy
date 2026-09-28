import 'package:flutter/material.dart';

import '../../../shared/notice/notice_board.dart';
import '../../../shared/notice/notice_data.dart';
import '../../../shared/widgets/reappear_reload.dart';
import '../../../shared/widgets/sub_page.dart';

/// S-8 공지. 학생 본인 기준이라 자녀 id 를 넘기지 않는다 — 서버가 토큰에서 찾는다.
class StudentNoticesPage extends StatefulWidget {
  const StudentNoticesPage({
    super.key,
    required this.controller,
    required this.openUrl,
  });

  final NoticeListController controller;
  final UrlOpener openUrl;

  @override
  State<StudentNoticesPage> createState() => _StudentNoticesPageState();
}

class _StudentNoticesPageState extends State<StudentNoticesPage>
    with ReappearReload<StudentNoticesPage> {
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(_onChanged);
    widget.controller.load(null);
  }

  @override
  void didUpdateWidget(covariant StudentNoticesPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_onChanged);
      widget.controller.addListener(_onChanged);
      widget.controller.load(null);
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

  @override
  void onReappear() => widget.controller.load(null);

  @override
  Widget build(BuildContext context) {
    return SubPageScroll(
      role: '학생',
      onRefresh: widget.controller.refresh,
      children: [
        const PageTitle(title: '학원 공지 · 안내'),
        NoticeBoard(
          controller: widget.controller,
          studentId: null,
          openUrl: widget.openUrl,
        ),
      ],
    );
  }
}
