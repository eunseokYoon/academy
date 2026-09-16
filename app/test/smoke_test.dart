import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/main.dart';

void main() {
  testWidgets('앱이 뜬다', (tester) async {
    await tester.pumpWidget(const AcademyApp());
    expect(find.text('부트스트랩'), findsOneWidget);
  });
}
