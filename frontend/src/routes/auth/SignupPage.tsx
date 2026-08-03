import { useState } from "react";
import type { FormEvent } from "react";
import { Link } from "react-router-dom";
import { post } from "../../shared/api/client";
import { errorMessage } from "../../shared/api/errors";
import type { Role } from "../../shared/api/types";
import { FormError } from "../../shared/components/FormError";
import { SubmitButton } from "../../shared/components/SubmitButton";
import { TextField } from "../../shared/components/TextField";
import { digitsOnly, formatPhone } from "../../shared/lib/phone";

interface SignupResult {
  role: Role;
  loginId: string;
  studentName: string;
  classRoomName?: string;
  initialPassword: string;
}

type Mode = "CLASS" | "PERSONAL";

/**
 * C-4. 코드 종류는 서버가 판별하지만 입력 항목이 달라 화면은 둘로 나눈다.
 *
 * <p>코드 유효성만 미리 확인해 주는 화면·API를 만들지 마라. 코드 하나로 유효 여부를
 * 알려주면 무작위 대입의 정답 판별기가 된다. 코드와 전화번호를 함께 제출해야 한다.
 */
export default function SignupPage() {
  const [mode, setMode] = useState<Mode>("CLASS");
  const [result, setResult] = useState<SignupResult | null>(null);

  if (result) return <SignupDone result={result} />;

  return (
    <div className="mx-auto min-h-screen w-full max-w-sm bg-slate-50 p-4 pt-10">
      <h1 className="text-2xl font-semibold text-slate-900">회원가입</h1>
      <p className="mt-1 text-sm text-slate-500">선생님께 받은 코드로 가입합니다.</p>

      {mode === "CLASS" ? (
        <ClassCodeForm onDone={setResult} onSwitch={() => setMode("PERSONAL")} />
      ) : (
        <PersonalCodeForm onDone={setResult} onSwitch={() => setMode("CLASS")} />
      )}

      <p className="mt-8 text-sm text-slate-500">
        이미 계정이 있으신가요?{" "}
        <Link to="/login" className="font-medium text-slate-900 underline">
          로그인
        </Link>
      </p>
    </div>
  );
}

interface FormProps {
  onDone: (result: SignupResult) => void;
  onSwitch: () => void;
}

/** 주 경로. 학생이 수업에서 들은 반 코드로 스스로 가입한다. */
function ClassCodeForm({ onDone, onSwitch }: FormProps) {
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [phone, setPhone] = useState("");
  const [parentPhone, setParentPhone] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setPending(true);
    try {
      onDone(
        await post<SignupResult>("/auth/signup", {
          code: code.trim().toUpperCase(),
          name: name.trim(),
          phone: digitsOnly(phone),
          parentPhone: digitsOnly(parentPhone),
        }),
      );
    } catch (e) {
      setError(errorMessage(e, "가입에 실패했습니다."));
    } finally {
      setPending(false);
    }
  }

  return (
    <>
      <form onSubmit={handleSubmit} className="mt-6 space-y-4">
        <TextField
          label="반 코드"
          placeholder="HK7F2Q"
          autoCapitalize="characters"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          required
        />
        <TextField
          label="이름"
          placeholder="서동환"
          autoComplete="name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />
        <TextField
          label="내 전화번호"
          type="tel"
          inputMode="numeric"
          placeholder="010-1111-2222"
          value={phone}
          onChange={(e) => setPhone(formatPhone(e.target.value))}
          required
        />
        <TextField
          label="보호자 번호"
          type="tel"
          inputMode="numeric"
          placeholder="010-9876-5432"
          hint="이 번호로 보호자 계정이 바로 만들어집니다. 정확히 입력하세요. 본인 번호와 달라야 합니다."
          value={parentPhone}
          onChange={(e) => setParentPhone(formatPhone(e.target.value))}
          required
        />
        <FormError message={error} />
        <SubmitButton pending={pending}>가입하기</SubmitButton>
      </form>

      <div className="mt-6 border-t border-slate-200 pt-4 text-sm text-slate-500">
        선생님께 개인 코드를 받으셨나요?{" "}
        <button type="button" onClick={onSwitch} className="font-medium text-slate-900 underline">
          개인 코드로 가입
        </button>
      </div>
    </>
  );
}

/**
 * 보조 경로. <b>학생 전용</b>이다 — 폰이 없거나 반 코드를 못 쓴 학생을 선생님이 대신 등록한 경우다.
 *
 * <p>학부모용 개인 코드는 없다. 학부모 계정은 학생 가입·등록 시점에 보호자 번호로
 * 바로 만들어지고, 초기 비밀번호 0000으로 로그인 화면에서 바로 들어간다.
 */
function PersonalCodeForm({ onDone, onSwitch }: FormProps) {
  const [code, setCode] = useState("");
  const [phone, setPhone] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setPending(true);
    try {
      onDone(
        await post<SignupResult>("/auth/signup", {
          code: code.trim().toUpperCase(),
          phone: digitsOnly(phone),
        }),
      );
    } catch (e) {
      setError(errorMessage(e, "가입에 실패했습니다."));
    } finally {
      setPending(false);
    }
  }

  return (
    <>
      <form onSubmit={handleSubmit} className="mt-6 space-y-4">
        <TextField
          label="코드"
          placeholder="K7F2QX"
          autoCapitalize="characters"
          value={code}
          onChange={(e) => setCode(e.target.value.toUpperCase())}
          required
        />
        <TextField
          label="전화번호"
          type="tel"
          inputMode="numeric"
          placeholder="010-1234-5678"
          hint="코드를 받으실 때 선생님께 알려 주신 번호여야 합니다."
          value={phone}
          onChange={(e) => setPhone(formatPhone(e.target.value))}
          required
        />
        <FormError message={error} />
        <SubmitButton pending={pending}>가입하기</SubmitButton>
      </form>

      <div className="mt-6 border-t border-slate-200 pt-4 text-sm text-slate-500">
        반 코드를 받으셨나요?{" "}
        <button type="button" onClick={onSwitch} className="font-medium text-slate-900 underline">
          반 코드로 가입
        </button>
      </div>
    </>
  );
}

function SignupDone({ result }: { result: SignupResult }) {
  return (
    <div className="mx-auto min-h-screen w-full max-w-sm bg-slate-50 p-4 pt-10">
      <h1 className="text-2xl font-semibold text-slate-900">가입이 완료되었습니다</h1>

      <dl className="mt-6 space-y-3 rounded-xl bg-white p-4 text-sm shadow-sm">
        <div className="flex justify-between">
          <dt className="text-slate-500">이름</dt>
          <dd className="font-medium text-slate-900">{result.studentName}</dd>
        </div>
        {result.classRoomName && (
          <div className="flex justify-between">
            <dt className="text-slate-500">반</dt>
            <dd className="font-medium text-slate-900">{result.classRoomName}</dd>
          </div>
        )}
        <div className="flex justify-between">
          <dt className="text-slate-500">아이디</dt>
          <dd className="font-mono font-medium text-slate-900">{result.loginId}</dd>
        </div>
      </dl>

      {/* 0000은 전원이 아는 값이라 바꾸기 전에는 다른 화면이 열리지 않는다 */}
      <div className="mt-4 rounded-xl bg-amber-50 p-4">
        <p className="text-sm font-semibold text-amber-900">
          초기 비밀번호는 {result.initialPassword} 입니다.
        </p>
        <p className="mt-1 text-sm text-amber-800">
          로그인한 뒤 바로 변경해 주세요. 변경 전에는 다른 화면을 볼 수 없습니다.
        </p>
      </div>

      <Link
        to="/login"
        className="mt-6 block rounded-lg bg-slate-900 px-4 py-3 text-center text-base
                   font-medium text-white"
      >
        로그인하러 가기
      </Link>
    </div>
  );
}
