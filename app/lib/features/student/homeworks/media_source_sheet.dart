import 'package:flutter/material.dart';

import 'submission_media.dart';

/// 카메라·앨범 중 하나. 버튼 하나가 둘 다 연다(2026-09-26 확정) — 웹의 파일
/// 선택이 폰에서 주는 선택지와 같다. 숙제 제출(S-3)과 게시판(S-9)이 같이 쓴다.
///
/// 루트 내비게이터에 띄운다 — 하단 탭 바 위에 떠야 한다. 닫으면 null 이다.
Future<MediaFrom?> chooseMediaSource(BuildContext context) {
  return showModalBottomSheet<MediaFrom>(
    context: context,
    useRootNavigator: true,
    backgroundColor: Colors.white,
    shape: const RoundedRectangleBorder(
      borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
    ),
    builder: (context) => SafeArea(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          ListTile(
            key: const Key('source-camera'),
            title: const Text('카메라로 찍기'),
            onTap: () => Navigator.of(context).pop(MediaFrom.camera),
          ),
          ListTile(
            key: const Key('source-gallery'),
            title: const Text('앨범에서 고르기'),
            onTap: () => Navigator.of(context).pop(MediaFrom.gallery),
          ),
        ],
      ),
    ),
  );
}
