/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        /**
         * 남색 한 줄기가 두 가지 일을 한다 — 900은 앱바 띠와 본문 잉크, 600은 링크·버튼.
         * 참고 디자인의 마룬이 하던 역할 그대로다.
         *
         * Tailwind 기본 blue(600 = #2563EB)를 쓰지 않는다. 그 형광 파랑은 IT 서비스로 읽힌다.
         * 여기 600은 채도를 낮춘 #256EC1이다 — 기본 blue보다 보라 기운이 없어 학원 쪽 무게가 난다.
         * 더 밝히려면 500까지 같이 올려라. 600만 올리면 두 단계가 붙어 램프가 무너진다.
         *
         * 상태색(emerald·amber·red·sky)은 브랜드색으로 바꾸지 마라.
         * 포인트가 파랑으로 옮겨간 덕분에 빨강이 "결석·위험" 하나만 뜻하게 됐다.
         */
        brand: {
          50: "#EFF4FC",
          100: "#DAE6F7",
          200: "#B8CEEF",
          300: "#8AAFE2",
          400: "#63A0E4",
          500: "#3C82D4",
          600: "#256EC1",
          700: "#1A4A8A",
          800: "#183D70",
          900: "#14294D",
          950: "#0D1B33",
        },
        /** 페이지 배경. 흰 카드가 떠 보이려면 배경이 흰색이 아니어야 한다. */
        paper: "#EDF1F7",
      },
      fontFamily: {
        sans: [
          "Pretendard Variable",
          "Pretendard",
          "-apple-system",
          "BlinkMacSystemFont",
          "system-ui",
          "Roboto",
          "Apple SD Gothic Neo",
          "Noto Sans KR",
          "Malgun Gothic",
          "sans-serif",
        ],
      },
      boxShadow: {
        /**
         * 회색이 아니라 남색이 섞인 그림자다. 회색 그림자는 이 배경과 따로 논다.
         *
         * sm은 Tailwind 기본값을 <b>덮어쓴 것</b>이다. 아직 손대지 않은 페이지들이
         * `bg-white rounded-xl shadow-sm`을 쓰고 있어서, 이 한 줄로 그쪽 카드까지
         * 같은 그림자를 갖게 된다. 페이지를 하나씩 고칠 필요가 없다.
         */
        sm: "0 1px 2px rgba(20,41,77,0.06), 0 4px 10px -6px rgba(20,41,77,0.10)",
        card: "0 1px 2px rgba(20,41,77,0.05), 0 6px 16px -6px rgba(20,41,77,0.10)",
        pill: "0 2px 8px -2px rgba(20,41,77,0.35)",
      },
    },
  },
  plugins: [],
};
