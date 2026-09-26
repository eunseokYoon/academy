import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:academy_app/core/theme/app_colors.dart';
import 'package:academy_app/shared/widgets/app_text_field.dart';

void main() {
  testWidgets('라벨과 힌트를 함께 그린다', (tester) async {
    // 웹은 placeholder 만 쓰지 않는다 — 라벨이 입력칸 위에 따로 있다.
    // placeholder 만 두면 입력을 시작한 순간 그 칸이 무엇인지 알 수 없다.
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '전화번호',
            controller: TextEditingController(),
            placeholder: '010-1234-5678',
            hint: '코드를 받으실 때 알려 주신 번호여야 합니다.',
          ),
        ),
      ),
    );

    expect(find.text('전화번호'), findsOneWidget);
    expect(find.text('010-1234-5678'), findsOneWidget);
    expect(find.text('코드를 받으실 때 알려 주신 번호여야 합니다.'), findsOneWidget);
  });

  testWidgets('힌트가 없으면 그 자리를 만들지 않는다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '비밀번호',
            controller: TextEditingController(),
          ),
        ),
      ),
    );
    // 라벨만 남는다
    expect(find.text('비밀번호'), findsOneWidget);
    expect(tester.takeException(), isNull);
  });

  testWidgets('포커스가 들어오면 바깥 링이 생긴다', (tester) async {
    // 웹의 focus:ring-4 ring-brand-600/15 다. InputDecoration 의
    // focusedBorder 는 두께만 있고 바깥 확산이 없어서 BoxShadow 로 얹는다.
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '전화번호',
            controller: TextEditingController(),
          ),
        ),
      ),
    );

    List<BoxShadow>? ringOf() {
      final box = tester.widget<DecoratedBox>(
        find
            .descendant(
              of: find.byType(AppTextField),
              matching: find.byType(DecoratedBox),
            )
            .first,
      );
      return (box.decoration as BoxDecoration).boxShadow;
    }

    expect(ringOf(), isEmpty);

    await tester.tap(find.byType(TextField));
    await tester.pumpAndSettle();

    final shadows = ringOf();
    expect(shadows, isNotEmpty);
    expect(shadows!.first.spreadRadius, 4);
    expect(shadows.first.blurRadius, 0);
  });

  testWidgets('입력 글자는 16 이상이다', (tester) async {
    // 16 보다 작으면 iOS 에서 입력할 때 화면이 확대된다.
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '전화번호',
            controller: TextEditingController(),
          ),
        ),
      ),
    );
    final field = tester.widget<TextField>(find.byType(TextField));
    expect(field.style?.fontSize, greaterThanOrEqualTo(16));
  });

  testWidgets('입력칸의 테두리 색이 slate300이다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '전화번호',
            controller: TextEditingController(),
          ),
        ),
      ),
    );
    final field = tester.widget<TextField>(find.byType(TextField));
    final decoration = field.decoration!;
    final border = decoration.enabledBorder as OutlineInputBorder;
    expect(
      border.borderSide.color,
      const Color(0xFFCBD5E1),
    ); // AppColors.slate300
  });

  testWidgets('입력칸의 모서리가 xl(12)이다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '전화번호',
            controller: TextEditingController(),
          ),
        ),
      ),
    );
    final field = tester.widget<TextField>(find.byType(TextField));
    final decoration = field.decoration!;
    final border = decoration.enabledBorder as OutlineInputBorder;
    expect(border.borderRadius, BorderRadius.circular(12));
  });

  testWidgets('포커스 링의 색이 brand600 15% 투명도다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '전화번호',
            controller: TextEditingController(),
          ),
        ),
      ),
    );

    await tester.tap(find.byType(TextField));
    await tester.pumpAndSettle();

    final box = tester.widget<DecoratedBox>(
      find
          .descendant(
            of: find.byType(AppTextField),
            matching: find.byType(DecoratedBox),
          )
          .first,
    );
    final shadows = (box.decoration as BoxDecoration).boxShadow;
    expect(shadows, isNotEmpty);
    final shadow = shadows!.first;
    // brand600 = 0xFF1E5AA8, with 15% alpha ≈ 0x261E5AA8
    expect(shadow.color.toARGB32(), 0x261E5AA8);
  });

  testWidgets('라벨의 스타일이 맞다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '전화번호',
            controller: TextEditingController(),
          ),
        ),
      ),
    );

    final label = tester.widget<Text>(find.text('전화번호'));
    expect(label.style?.fontSize, 14);
    expect(label.style?.fontWeight, FontWeight.w500);
    expect(label.style?.color?.toARGB32(), AppColors.slate700.toARGB32());
  });

  testWidgets('힌트의 스타일이 맞다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '전화번호',
            controller: TextEditingController(),
            hint: '설명 텍스트',
          ),
        ),
      ),
    );

    final hint = tester.widget<Text>(find.text('설명 텍스트'));
    expect(hint.style?.fontSize, 12);
    expect(hint.style?.color?.toARGB32(), AppColors.slate500.toARGB32());
  });

  testWidgets('placeholder 색이 slate400이다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '전화번호',
            controller: TextEditingController(),
            placeholder: '010-1234-5678',
          ),
        ),
      ),
    );

    final field = tester.widget<TextField>(find.byType(TextField));
    final hintStyle = field.decoration!.hintStyle as TextStyle;
    expect(hintStyle.color?.toARGB32(), AppColors.slate400.toARGB32());
  });

  testWidgets('입력칸의 배경이 white이다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '전화번호',
            controller: TextEditingController(),
          ),
        ),
      ),
    );

    final field = tester.widget<TextField>(find.byType(TextField));
    final fillColor = field.decoration!.fillColor;
    expect(fillColor?.toARGB32(), Colors.white.toARGB32());
  });

  testWidgets('포커스된 테두리의 색이 brand600이다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '전화번호',
            controller: TextEditingController(),
          ),
        ),
      ),
    );

    await tester.tap(find.byType(TextField));
    await tester.pumpAndSettle();

    final field = tester.widget<TextField>(find.byType(TextField));
    final focusedBorder = field.decoration!.focusedBorder as OutlineInputBorder;
    expect(
      focusedBorder.borderSide.color.toARGB32(),
      AppColors.brand600.toARGB32(),
    );
  });

  testWidgets('obscureText:true를 inner TextField로 전달한다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '비밀번호',
            controller: TextEditingController(),
            obscureText: true,
          ),
        ),
      ),
    );

    final field = tester.widget<TextField>(find.byType(TextField));
    expect(field.obscureText, isTrue);
  });

  testWidgets('obscureText를 지정하지 않으면 inner TextField는 false다', (tester) async {
    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: AppTextField(
            label: '전화번호',
            controller: TextEditingController(),
          ),
        ),
      ),
    );

    final field = tester.widget<TextField>(find.byType(TextField));
    expect(field.obscureText, isFalse);
  });
}
