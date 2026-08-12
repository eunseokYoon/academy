import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "react-router-dom";
import { errorMessage } from "../../../shared/api/errors";
import { FormError } from "../../../shared/components/FormError";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { TextField } from "../../../shared/components/TextField";
import { digitsOnly, formatPhone } from "../../../shared/lib/phone";
import { createStudent, listClassRooms } from "../api";
import type { StudentCreated } from "../api";
import { SignupCodeCard } from "./SignupCodeCard";

/**
 * T-2 등록. 주 경로가 아니라 보조 경로다 — 폰이 없거나 코드를 못 쓰는 학생을 대신 넣어 준다.
 * 200명을 여기서 다 등록하려 하면 코드 400장을 구두로 전달하게 된다.
 *
 * <p>전화번호 칸이 둘인 이유는 각각 학생·학부모의 로그인 아이디가 되기 때문이다.
 * 같은 번호를 넣으면 서버가 409로 막는다.
 */
export default function StudentNewPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const [name, setName] = useState("");
  const [studentPhone, setStudentPhone] = useState("");
  const [parentPhone, setParentPhone] = useState("");
  const [memo, setMemo] = useState("");
  const [classRoomIds, setClassRoomIds] = useState<number[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [created, setCreated] = useState<StudentCreated | null>(null);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms("ACTIVE"),
  });

  const mutation = useMutation({
    mutationFn: () =>
      createStudent({
        name: name.trim(),
        studentPhone: digitsOnly(studentPhone),
        parentPhone: digitsOnly(parentPhone),
        memo: memo.trim() || null,
        classRoomIds,
      }),
    onSuccess: async (result) => {
      setError(null);
      setCreated(result);
      await queryClient.invalidateQueries({ queryKey: ["teacher", "students"] });
    },
    onError: (e) => setError(errorMessage(e, "학생을 등록하지 못했습니다.")),
  });

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate();
  }

  // 등록 직후 코드를 바로 보여준다. 별도 화면으로 미루면 실제로 안 쓴다
  if (created) {
    return (
      <div className="space-y-4">
        <h2 className="text-lg font-semibold text-slate-900">{name.trim()} 등록 완료</h2>
        <p className="text-sm text-slate-500">
          아래 코드를 학생에게 전달하세요. 7일 안에 가입해야 합니다.
        </p>
        <SignupCodeCard
          target="STUDENT"
          code={created.signupCode.code}
          phone={created.signupCode.phone}
          expiresAt={created.signupCode.expiresAt}
        />
        {/* 학부모는 코드가 없다. 안내 문구가 없으면 선생님이 코드를 찾아 헤맨다 */}
        <div className="rounded-xl bg-brand-50 p-4 text-sm text-brand-900 ring-1 ring-inset
                        ring-brand-200">
          <p className="font-semibold">보호자 계정은 이미 만들어졌습니다</p>
          <p className="mt-1 leading-relaxed text-brand-800">
            보호자 번호로 바로 로그인할 수 있습니다. 전달할 코드는 없고,
            <b> 초기 비밀번호는 0000</b>입니다. 첫 로그인에서 비밀번호를 바꿔야 다른 화면이 열립니다.
          </p>
        </div>
        <div className="flex gap-2">
          <button
            type="button"
            onClick={() => navigate(`/teacher/students/${created.studentId}`)}
            className="flex-1 rounded-lg bg-slate-900 px-4 py-3 text-base font-medium text-white"
          >
            상세로 이동
          </button>
          <button
            type="button"
            onClick={() => navigate("/teacher/students")}
            className="flex-1 rounded-lg border border-slate-300 px-4 py-3 text-base
                       font-medium text-slate-700"
          >
            목록으로
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <h2 className="text-lg font-semibold text-slate-900">학생 등록</h2>
      <p className="rounded-lg bg-slate-100 px-3 py-2 text-xs text-slate-600">
        계정은 여기서 만들지 않습니다. 코드로 본인이 가입할 때 생기고, 초기 비밀번호는 0000입니다.
      </p>

      <form onSubmit={handleSubmit} className="space-y-4 rounded-xl bg-white p-4 shadow-sm">
        <TextField
          label="이름"
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />
        <TextField
          label="학생 전화번호"
          type="tel"
          inputMode="numeric"
          hint="가입 후 학생의 로그인 아이디가 됩니다."
          value={studentPhone}
          onChange={(e) => setStudentPhone(formatPhone(e.target.value))}
          required
        />
        <TextField
          label="보호자 전화번호"
          type="tel"
          inputMode="numeric"
          hint="학생 번호와 달라야 합니다. 같으면 둘 중 하나는 로그인할 수 없습니다."
          value={parentPhone}
          onChange={(e) => setParentPhone(formatPhone(e.target.value))}
          required
        />
        <TextField label="메모" value={memo} onChange={(e) => setMemo(e.target.value)} />

        <fieldset>
          <legend className="text-sm font-medium text-slate-700">반 배정</legend>
          <div className="mt-2 space-y-1">
            {(classRooms.data ?? []).map((room) => (
              <label key={room.classRoomId} className="flex items-center gap-2 text-sm">
                <input
                  type="checkbox"
                  checked={classRoomIds.includes(room.classRoomId)}
                  onChange={(e) =>
                    setClassRoomIds((prev) =>
                      e.target.checked
                        ? [...prev, room.classRoomId]
                        : prev.filter((id) => id !== room.classRoomId),
                    )
                  }
                  className="h-4 w-4 rounded border-slate-300"
                />
                {room.name}
              </label>
            ))}
            {classRooms.data?.length === 0 && (
              <p className="text-sm text-slate-500">활성 반이 없습니다. 반을 먼저 만드세요.</p>
            )}
          </div>
        </fieldset>

        <FormError message={error} />
        <SubmitButton pending={mutation.isPending}>등록하고 코드 받기</SubmitButton>
      </form>
    </div>
  );
}
