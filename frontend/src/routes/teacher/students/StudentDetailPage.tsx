import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Link, useNavigate, useParams } from "react-router-dom";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { CopyButton } from "../../../shared/components/CopyButton";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { TextField } from "../../../shared/components/TextField";
import { digitsOnly, formatPhone } from "../../../shared/lib/phone";
import {
  deleteStudent,
  getStudent,
  issueSignupCode,
  resetPassword,
  restoreStudent,
  updateStudent,
  withdrawStudent,
} from "../api";
import type { CodeTarget, StudentDetail } from "../api";
import { today } from "../format";
import { SignupCodeCard } from "./SignupCodeCard";

export default function StudentDetailPage() {
  const studentId = Number(useParams().studentId);
  const queryClient = useQueryClient();

  const { data, isPending } = useQuery({
    queryKey: ["teacher", "students", studentId],
    queryFn: () => getStudent(studentId),
  });

  function refresh(updated?: StudentDetail) {
    if (updated) queryClient.setQueryData(["teacher", "students", studentId], updated);
    return queryClient.invalidateQueries({ queryKey: ["teacher", "students"] });
  }

  if (isPending || !data) return <p className="text-sm text-slate-400">불러오는 중…</p>;

  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2">
        <h2 className="text-lg font-semibold text-slate-900">{data.name}</h2>
        {data.status === "WITHDRAWN" && <Badge tone="neutral">퇴원</Badge>}
        {!data.studentSignedUp && <Badge tone="warn">학생 미가입</Badge>}
        {!data.parentLinked && <Badge tone="warn">학부모 미연결</Badge>}
      </div>

      <ProfileSection student={data} onDone={refresh} />
      <ClassRoomSection student={data} />
      <SignupCodeSection student={data} onDone={refresh} />
      <PasswordSection student={data} />
      <DangerSection student={data} onDone={refresh} />

      <Link to="/teacher/students" className="block py-2 text-sm text-slate-500 underline">
        목록으로
      </Link>
    </div>
  );
}

interface SectionProps {
  student: StudentDetail;
  onDone: (updated?: StudentDetail) => Promise<unknown>;
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="rounded-xl bg-white p-4 shadow-sm">
      <h3 className="text-sm font-semibold text-slate-900">{title}</h3>
      <div className="mt-3">{children}</div>
    </section>
  );
}

/** 번호는 가입 여부에 따라 바뀌는 곳이 다르다. 가입 전이면 코드의 대조 번호, 후면 로그인 아이디다. */
function ProfileSection({ student, onDone }: SectionProps) {
  const [name, setName] = useState(student.name);
  const [memo, setMemo] = useState(student.memo ?? "");
  const [studentPhone, setStudentPhone] = useState(formatPhone(student.studentPhone ?? ""));
  const [parentPhone, setParentPhone] = useState(formatPhone(student.parentPhone ?? ""));
  const [error, setError] = useState<string | null>(null);
  const [saved, setSaved] = useState(false);

  const mutation = useMutation({
    mutationFn: () => {
      const body: Record<string, string> = { name: name.trim(), memo: memo.trim() };
      if (digitsOnly(studentPhone) !== (student.studentPhone ?? "")) {
        body.studentPhone = digitsOnly(studentPhone);
      }
      if (digitsOnly(parentPhone) !== (student.parentPhone ?? "")) {
        body.parentPhone = digitsOnly(parentPhone);
      }
      return updateStudent(student.studentId, body);
    },
    onSuccess: async (updated) => {
      setError(null);
      setSaved(true);
      await onDone(updated);
    },
    onError: (e) => {
      setSaved(false);
      setError(errorMessage(e, "수정하지 못했습니다."));
    },
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaved(false);
    mutation.mutate();
  }

  return (
    <Section title="기본 정보">
      <form onSubmit={handleSubmit} className="space-y-3">
        <TextField label="이름" value={name} onChange={(e) => setName(e.target.value)} required />
        <TextField
          label="학생 전화번호"
          type="tel"
          inputMode="numeric"
          hint={student.studentSignedUp ? "가입 완료. 바꾸면 로그인 아이디가 바뀝니다." : "미가입. 코드의 대조 번호입니다."}
          value={studentPhone}
          onChange={(e) => setStudentPhone(formatPhone(e.target.value))}
        />
        <TextField
          label="보호자 전화번호"
          type="tel"
          inputMode="numeric"
          hint={
            student.parentLinked
              ? "이미 가입한 학부모입니다. 번호는 학부모가 직접 바꿉니다."
              : "미연결. 코드의 대조 번호입니다."
          }
          value={parentPhone}
          disabled={student.parentLinked}
          onChange={(e) => setParentPhone(formatPhone(e.target.value))}
        />
        <TextField label="메모" value={memo} onChange={(e) => setMemo(e.target.value)} />
        <p className="text-xs text-slate-400">
          등록 {student.createdAt.slice(0, 10)}
          {student.parentName && ` · 보호자 ${student.parentName}`}
          {student.withdrawnAt && ` · 퇴원 ${student.withdrawnAt}`}
        </p>
        <FormError message={error} />
        {saved && <p className="text-sm text-emerald-700">저장했습니다.</p>}
        <SubmitButton pending={mutation.isPending}>저장</SubmitButton>
      </form>
    </Section>
  );
}

function ClassRoomSection({ student }: { student: StudentDetail }) {
  return (
    <Section title="반">
      {student.classRooms.length === 0 ? (
        <p className="text-sm text-slate-500">
          배정된 반이 없습니다. 반 관리 화면에서 배정하세요.
        </p>
      ) : (
        <ul className="divide-y divide-slate-100">
          {student.classRooms.map((room) => (
            <li key={room.classRoomId} className="flex justify-between py-2 text-sm">
              <Link to={`/teacher/class-rooms/${room.classRoomId}`} className="text-slate-700 underline">
                {room.name}
              </Link>
              <span className="text-slate-400">{room.joinedAt} 배정</span>
            </li>
          ))}
        </ul>
      )}
    </Section>
  );
}

/** 미사용 코드가 있으면 그대로 보여주고, 잃어버렸으면 재발급한다(이전 코드는 즉시 무효). */
function SignupCodeSection({ student, onDone }: SectionProps) {
  const [target, setTarget] = useState<CodeTarget>("STUDENT");
  const [phone, setPhone] = useState("");
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () =>
      issueSignupCode(student.studentId, {
        target,
        phone: phone.trim() ? digitsOnly(phone) : undefined,
      }),
    onSuccess: async () => {
      setError(null);
      setPhone("");
      await onDone();
    },
    onError: (e) => setError(errorMessage(e, "코드를 발급하지 못했습니다.")),
  });

  const bothSignedUp = student.studentSignedUp && student.parentLinked;

  return (
    <Section title="회원가입 코드">
      {student.signupCodes.length === 0 ? (
        <p className="text-sm text-slate-500">
          {bothSignedUp ? "둘 다 가입을 마쳤습니다." : "발급된 코드가 없습니다."}
        </p>
      ) : (
        <div className="space-y-3">
          {student.signupCodes.map((code) => (
            <SignupCodeCard
              key={code.code}
              target={code.target}
              code={code.code}
              phone={code.phone}
              expiresAt={code.expiresAt}
            />
          ))}
        </div>
      )}

      {!bothSignedUp && (
        <form
          className="mt-4 space-y-2 border-t border-slate-100 pt-4"
          onSubmit={(e) => {
            e.preventDefault();
            mutation.mutate();
          }}
        >
          <p className="text-xs text-slate-500">
            재발급하면 이전 코드는 즉시 무효입니다. 번호를 비우면 기존 번호를 유지합니다.
          </p>
          <div className="flex gap-2">
            <select
              value={target}
              onChange={(e) => setTarget(e.target.value as CodeTarget)}
              className="rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm"
            >
              <option value="STUDENT" disabled={student.studentSignedUp}>
                학생용
              </option>
              <option value="PARENT" disabled={student.parentLinked}>
                학부모용
              </option>
            </select>
            <input
              value={phone}
              onChange={(e) => setPhone(formatPhone(e.target.value))}
              placeholder="번호 변경 시에만 입력"
              inputMode="numeric"
              className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-sm"
            />
            <button
              type="submit"
              disabled={mutation.isPending}
              className="shrink-0 rounded-lg border border-slate-300 px-3 py-2 text-sm
                         font-medium text-slate-700 disabled:opacity-40"
            >
              재발급
            </button>
          </div>
          <FormError message={error} />
        </form>
      )}
    </Section>
  );
}

/** 비밀번호 분실 시 유일한 복구 경로다. 임시 비밀번호는 이 화면에서 한 번만 보인다. */
function PasswordSection({ student }: { student: StudentDetail }) {
  const [target, setTarget] = useState<CodeTarget>("STUDENT");
  const [issued, setIssued] = useState<{ loginId: string; temporaryPassword: string } | null>(null);
  const [error, setError] = useState<string | null>(null);

  const mutation = useMutation({
    mutationFn: () => resetPassword(student.studentId, { target, newPassword: null }),
    onSuccess: (result) => {
      setError(null);
      setIssued(result);
    },
    onError: (e) => setError(errorMessage(e, "초기화하지 못했습니다.")),
  });

  // 계정이 없으면 초기화할 것도 없다. 눌러 봐야 400이라 아예 막는다
  const available = target === "PARENT" ? student.parentLinked : student.studentSignedUp;

  return (
    <Section title="비밀번호 초기화">
      <p className="text-xs text-slate-500">
        비밀번호를 잃어버렸을 때 씁니다. 아직 가입하지 않았다면 초기화가 아니라 코드 재발급입니다.
      </p>
      <div className="mt-2 flex gap-2">
        <select
          value={target}
          onChange={(e) => setTarget(e.target.value as CodeTarget)}
          className="rounded-lg border border-slate-300 bg-white px-2 py-2 text-sm"
        >
          <option value="STUDENT" disabled={!student.studentSignedUp}>
            학생
          </option>
          <option value="PARENT" disabled={!student.parentLinked}>
            학부모
          </option>
        </select>
        <button
          type="button"
          disabled={mutation.isPending || !available}
          onClick={() => mutation.mutate()}
          className="rounded-lg border border-slate-300 px-3 py-2 text-sm font-medium
                     text-slate-700 disabled:opacity-40"
        >
          초기화
        </button>
      </div>
      {!available && (
        <p className="mt-1 text-xs text-slate-500">
          아직 가입하지 않았습니다. 위에서 코드를 재발급하세요.
        </p>
      )}
      <FormError message={error} />

      {issued && (
        <Modal title="임시 비밀번호" onClose={() => setIssued(null)}>
          <p className="text-sm text-slate-600">
            이 화면을 닫으면 다시 볼 수 없습니다. 지금 전달하세요.
          </p>
          <div className="mt-3 rounded-lg bg-slate-50 p-3">
            <p className="text-sm text-slate-500">아이디 {formatPhone(issued.loginId)}</p>
            <div className="mt-1 flex items-center gap-2">
              <span className="flex-1 font-mono text-2xl tracking-widest text-slate-900">
                {issued.temporaryPassword}
              </span>
              <CopyButton value={issued.temporaryPassword} />
            </div>
          </div>
          <p className="mt-3 text-xs text-slate-500">
            다음 로그인에서 비밀번호를 반드시 변경하게 됩니다.
          </p>
        </Modal>
      )}
    </Section>
  );
}

/** 퇴원과 삭제는 쓰임이 다르다. 실제로 다닌 학생은 언제나 퇴원이다. */
function DangerSection({ student, onDone }: SectionProps) {
  const navigate = useNavigate();
  const [withdrawnAt, setWithdrawnAt] = useState(today());
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const withdraw = useMutation({
    mutationFn: () => withdrawStudent(student.studentId, withdrawnAt),
    onSuccess: async (updated) => {
      setError(null);
      await onDone(updated);
    },
    onError: (e) => setError(errorMessage(e, "퇴원 처리하지 못했습니다.")),
  });

  const restore = useMutation({
    mutationFn: () => restoreStudent(student.studentId),
    onSuccess: async () => {
      setError(null);
      await onDone();
    },
    onError: (e) => setError(errorMessage(e, "복구하지 못했습니다.")),
  });

  const remove = useMutation({
    mutationFn: () => deleteStudent(student.studentId),
    onSuccess: async () => {
      setConfirmDelete(false);
      await onDone();
      navigate("/teacher/students");
    },
    onError: (e) => {
      setConfirmDelete(false);
      setError(errorMessage(e, "삭제하지 못했습니다."));
    },
  });

  return (
    <Section title="퇴원 · 삭제">
      {student.status === "ENROLLED" ? (
        <div className="space-y-2">
          <label className="block text-sm font-medium text-slate-700">
            퇴원일
            <input
              type="date"
              value={withdrawnAt}
              onChange={(e) => setWithdrawnAt(e.target.value)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2 text-base"
            />
          </label>
          <button
            type="button"
            disabled={withdraw.isPending}
            onClick={() => withdraw.mutate()}
            className="w-full rounded-lg border border-slate-300 px-4 py-2.5 text-sm
                       font-medium text-slate-700"
          >
            퇴원 처리
          </button>
          <p className="text-xs text-slate-500">
            기록은 지우지 않고 상태만 바꿉니다. 로그인은 차단됩니다.
          </p>
        </div>
      ) : (
        <div className="space-y-2">
          <button
            type="button"
            disabled={restore.isPending}
            onClick={() => restore.mutate()}
            className="w-full rounded-lg border border-slate-300 px-4 py-2.5 text-sm
                       font-medium text-slate-700"
          >
            퇴원 취소
          </button>
          <p className="text-xs text-slate-500">
            반 배정은 되살아나지 않습니다. 복구 후 반 관리에서 다시 배정하세요.
          </p>
        </div>
      )}

      <div className="mt-4 border-t border-slate-100 pt-4">
        <button
          type="button"
          onClick={() => setConfirmDelete(true)}
          className="text-sm font-medium text-red-600 underline"
        >
          이 학생 삭제
        </button>
        <p className="mt-1 text-xs text-slate-500">
          반 코드로 잘못 들어온 사람을 지우는 기능입니다. 실제로 다닌 학생에게는 쓰지 마세요.
        </p>
      </div>

      <FormError message={error} />

      {confirmDelete && (
        <Modal
          title="정말 삭제할까요?"
          onClose={() => setConfirmDelete(false)}
          footer={
            <>
              <button
                type="button"
                onClick={() => setConfirmDelete(false)}
                className="flex-1 rounded-lg border border-slate-300 px-4 py-2.5 text-sm
                           font-medium text-slate-700"
              >
                취소
              </button>
              <button
                type="button"
                disabled={remove.isPending}
                onClick={() => remove.mutate()}
                className="flex-1 rounded-lg bg-red-600 px-4 py-2.5 text-sm font-medium text-white
                           disabled:opacity-40"
              >
                삭제
              </button>
            </>
          }
        >
          {/* 되돌릴 수 없다. 실제 학생을 잘못 지우면 복구 경로가 없다 */}
          <dl className="space-y-1 text-sm">
            <div className="flex justify-between">
              <dt className="text-slate-500">이름</dt>
              <dd className="font-medium text-slate-900">{student.name}</dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">전화번호</dt>
              <dd className="text-slate-900">
                {student.studentPhone ? formatPhone(student.studentPhone) : "없음"}
              </dd>
            </div>
            <div className="flex justify-between">
              <dt className="text-slate-500">등록일시</dt>
              <dd className="text-slate-900">{student.createdAt.slice(0, 16).replace("T", " ")}</dd>
            </div>
          </dl>
          <p className="mt-3 text-sm text-red-700">
            되돌릴 수 없습니다. 출석·성적 기록이 있으면 삭제되지 않고 퇴원 처리를 안내합니다.
          </p>
        </Modal>
      )}
    </Section>
  );
}
