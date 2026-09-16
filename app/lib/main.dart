import 'package:flutter/material.dart';

void main() {
  runApp(const AcademyApp());
}

class AcademyApp extends StatelessWidget {
  const AcademyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return const MaterialApp(
      title: '학원',
      home: Scaffold(body: Center(child: Text('부트스트랩'))),
    );
  }
}
