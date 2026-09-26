import 'package:flutter/material.dart';

import '../../core/theme/app_colors.dart';
import '../../core/theme/app_theme.dart';
import 'section.dart';

/// 홈에 보이는 공지 한 줄.
class NoticeSummary {
  const NoticeSummary({
    required this.id,
    required this.title,
    required this.createdAt,
    this.pinned = false,
  });

  final int id;
  final String title;

  /// 이미 `MM/DD` 로 만들어진 문자열이다. **앱에서 날짜를 다시 파싱하지 마라** —
  /// 화면이 API 모델의 `publishedAt` 에서 잘라 넘긴다(웹도 목록에서 자른다).
  final String createdAt;

  final bool pinned;
}

/// 홈의 공지 블록. **학생 홈(S-1)과 학부모 홈(P-1)이 같은 것을 본다** —
/// 두 화면이 어긋나면 "엄마 폰에는 다르게 나온다"는 문의가 된다.
///
/// 읽음 표시는 만들지 않는다(범위 밖). 그래서 필의 숫자는 "안 읽은 수"가 아니라
/// 전체 건수다 — 줄어들지 않는 게 정상이다.
///
/// **흰 카드가 아니라 [TintBlock] 한 장이다.** 공지는 급한 게 아니라 읽을 것이라
/// 남색(brand)이다. 주황은 「학생이 아직 처리 안 한 것」에만 쓴다.
///
/// 바깥 여백은 화면이 준다(웹도 `<div className="mt-5">` 가 감싼다).
class NoticeCard extends StatelessWidget {
  const NoticeCard({
    super.key,
    required this.totalCount,
    required this.recent,
    required this.onTapAll,
    required this.onTapOne,
  });

  final int totalCount;
  final List<NoticeSummary> recent;
  final VoidCallback onTapAll;

  /// 줄의 id 를 넘긴다. **다만 화면은 상세가 아니라 목록으로 간다** —
  /// 웹의 줄도 `<Link to={to}>` 로 목록으로 가고(읽음 표시가 없어 상세 진입이
  /// 없다), 홈이 `onTapOne: (_) => 목록` 으로 받는다.
  final ValueChanged<int> onTapOne;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      mainAxisSize: MainAxisSize.min,
      children: [
        // SectionHead 가 자기 mb-2 를 갖는다. 사이에 SizedBox 를 넣지 마라.
        SectionHead(
          tone: SectionTone.brand,
          title: '학원 공지',
          count: totalCount,
          onTapAction: onTapAll,
        ),
        // 비어도 블록은 남긴다 — 사라지면 공지가 있는 자리를 잊는다.
        if (recent.isEmpty)
          TintBlock(
            tone: SectionTone.brand,
            children: [
              Padding(
                // 웹 `px-4 py-5`.
                padding: const EdgeInsets.symmetric(
                  horizontal: 16,
                  vertical: 20,
                ),
                child: Text(
                  '등록된 공지가 없습니다.',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 14,
                    color: AppColors.brand700.withValues(alpha: 0.7),
                  ),
                ),
              ),
            ],
          )
        else
          TintBlock(
            tone: SectionTone.brand,
            children: [
              for (final notice in recent)
                _NoticeRow(notice: notice, onTap: onTapOne),
            ],
          ),
      ],
    );
  }
}

class _NoticeRow extends StatelessWidget {
  const _NoticeRow({required this.notice, required this.onTap});

  final NoticeSummary notice;
  final ValueChanged<int> onTap;

  @override
  Widget build(BuildContext context) {
    // InkWell 의 물결은 가장 가까운 Material 조상 위에 그려진다. TintBlock 은
    // Container 라 Material 이 아니어서, 감싸지 않으면 물결이 블록 뒤의 흰
    // Scaffold 면에 그려져 옅은 남색 틴트를 덮는다.
    return Material(
      key: const Key('notice-row'),
      type: MaterialType.transparency,
      child: InkWell(
        onTap: () => onTap(notice.id),
        // 웹 `active:bg-brand-100/60`.
        highlightColor: AppColors.brand100.withValues(alpha: 0.6),
        child: Padding(
          // 웹 `px-3.5 py-2.5`.
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
          child: Row(
            children: [
              // 고정은 옅은 배지가 아니라 채운 남색이다. 옅게 두면 남색 면 위에서
              // 배경과 붙어 "고정"인지 아닌지가 안 읽힌다.
              if (notice.pinned) ...[
                Container(
                  key: const Key('notice-pinned'),
                  // 웹 `px-1.5 py-0.5`.
                  padding: const EdgeInsets.symmetric(
                    horizontal: 6,
                    vertical: 2,
                  ),
                  decoration: BoxDecoration(
                    color: AppColors.brand600,
                    // 웹 `rounded` = 4px. section.dart 의 바(`rounded-sm` = 2)와
                    // 같은 방식으로 웹 값을 그대로 적는다.
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: const Text(
                    '고정',
                    style: TextStyle(
                      fontSize: 9.5,
                      fontWeight: FontWeight.w700,
                      color: Colors.white,
                    ),
                  ),
                ),
                const SizedBox(width: 8),
              ],
              Expanded(
                child: Text(
                  notice.title,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: const TextStyle(
                    fontSize: 13,
                    color: AppColors.brand950,
                  ),
                ),
              ),
              // 웹 `gap-2`.
              const SizedBox(width: 8),
              Text(
                notice.createdAt,
                style: TextStyle(
                  fontSize: 11,
                  color: AppColors.brand600.withValues(alpha: 0.7),
                  fontFeatures: kTabularFigures,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
