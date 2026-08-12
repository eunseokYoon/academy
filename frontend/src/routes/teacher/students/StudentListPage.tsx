import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "react-router-dom";
import { Badge } from "../../../shared/components/Badge";
import { formatPhone } from "../../../shared/lib/phone";
import { listClassRooms, listStudents } from "../api";
import type { StudentListItem, StudentStatus } from "../api";

const SIZE = 20;

/**
 * T-2 목록. 두 가지 일을 한다.
 *
 * <p>하나는 미가입자 찾기 — 200명 중 아직 가입하지 않은 사람에게 코드를 다시 알려주는 것이
 * 선생님의 실제 업무다. 다른 하나는 제3자 탐지 — 반 코드는 아는 사람 누구나 쓸 수 있어서
 * 등록 기간에는 최근 가입순 상단만 매일 훑고 모르는 이름을 지운다.
 */
export default function StudentListPage() {
  const [classRoomId, setClassRoomId] = useState<number | "">("");
  const [status, setStatus] = useState<StudentStatus | "">("ENROLLED");
  const [sort, setSort] = useState<"name" | "recent">("name");
  const [keyword, setKeyword] = useState("");
  const [search, setSearch] = useState("");
  const [page, setPage] = useState(0);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });

  const params = {
    classRoomId: classRoomId === "" ? undefined : classRoomId,
    status: status === "" ? undefined : status,
    keyword: search || undefined,
    sort,
    page,
    size: SIZE,
  };
  const students = useQuery({
    queryKey: ["teacher", "students", params],
    queryFn: () => listStudents(params),
  });

  function reset<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h2 className="text-lg font-semibold text-slate-900">학생 관리</h2>
        <Link
          to="/teacher/students/new"
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white"
        >
          학생 등록
        </Link>
      </div>

      <form
        className="space-y-2 rounded-xl bg-white p-3 shadow-sm"
        onSubmit={(e) => {
          e.preventDefault();
          setSearch(keyword.trim());
          setPage(0);
        }}
      >
        <div className="flex gap-2">
          <input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="이름 또는 전화번호 뒷자리"
            className="min-w-0 flex-1 rounded-lg border border-slate-300 px-3 py-2 text-base
                       outline-none focus:border-slate-900"
          />
          <button
            type="submit"
            className="shrink-0 rounded-lg border border-slate-300 px-3 py-2 text-sm
                       font-medium text-slate-700"
          >
            검색
          </button>
        </div>
        <div className="grid grid-cols-3 gap-2 text-sm">
          <select
            value={classRoomId}
            onChange={(e) => reset(setClassRoomId)(e.target.value === "" ? "" : Number(e.target.value))}
            className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          >
            <option value="">전체 반</option>
            {(classRooms.data ?? []).map((room) => (
              <option key={room.classRoomId} value={room.classRoomId}>
                {room.name}
              </option>
            ))}
          </select>
          <select
            value={status}
            onChange={(e) => reset(setStatus)(e.target.value as StudentStatus | "")}
            className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          >
            <option value="ENROLLED">재원</option>
            <option value="WITHDRAWN">퇴원</option>
            <option value="">전체</option>
          </select>
          <select
            value={sort}
            onChange={(e) => reset(setSort)(e.target.value as "name" | "recent")}
            className="rounded-lg border border-slate-300 bg-white px-2 py-2"
          >
            <option value="name">이름순</option>
            <option value="recent">최근 가입순</option>
          </select>
        </div>
        {sort === "recent" && (
          <p className="text-xs text-amber-700">
            반 코드는 아는 사람 누구나 가입합니다. 모르는 이름이 있으면 상세에서 삭제하세요.
          </p>
        )}
      </form>

      {students.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : (
        <>
          <p className="text-sm text-slate-500">{students.data?.totalElements ?? 0}명</p>
          <ul className="space-y-2">
            {(students.data?.items ?? []).map((student) => (
              <StudentRow key={student.studentId} student={student} showJoinedAt={sort === "recent"} />
            ))}
          </ul>
          {students.data && students.data.items.length === 0 && (
            <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
              조건에 맞는 학생이 없습니다.
            </p>
          )}
          <Pager
            page={page}
            totalPages={students.data?.totalPages ?? 0}
            onChange={setPage}
          />
        </>
      )}
    </div>
  );
}

function StudentRow({ student, showJoinedAt }: { student: StudentListItem; showJoinedAt: boolean }) {
  return (
    <li>
      <Link
        to={`/teacher/students/${student.studentId}`}
        className="block rounded-xl bg-white p-3 shadow-sm"
      >
        <div className="flex items-center gap-2">
          <span className="font-medium text-slate-900">{student.name}</span>
          {student.status === "WITHDRAWN" && <Badge tone="neutral">퇴원</Badge>}
          {/* 미가입자를 눈에 띄게 표시하는 것이 이 화면의 핵심이다 */}
          {!student.studentSignedUp && <Badge tone="warn">학생 미가입</Badge>}
          {!student.parentLinked && <Badge tone="warn">학부모 미연결</Badge>}
        </div>
        <p className="mt-1 text-sm text-slate-500">
          {student.classRooms.length > 0 ? student.classRooms.join(" · ") : "반 미배정"}
        </p>
        <p className="mt-0.5 text-xs text-slate-400">
          학생 {student.studentPhone ? formatPhone(student.studentPhone) : "번호 없음"} · 보호자{" "}
          {student.parentPhone ? formatPhone(student.parentPhone) : "번호 없음"}
          {showJoinedAt && ` · ${student.createdAt.slice(0, 10)} 등록`}
        </p>
      </Link>
    </li>
  );
}

function Pager({
  page,
  totalPages,
  onChange,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;
  return (
    <div className="flex items-center justify-center gap-4 py-2 text-sm">
      <button
        type="button"
        disabled={page === 0}
        onClick={() => onChange(page - 1)}
        className="rounded-lg border border-slate-300 px-3 py-1.5 disabled:opacity-40"
      >
        이전
      </button>
      <span className="text-slate-500">
        {page + 1} / {totalPages}
      </span>
      <button
        type="button"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
        className="rounded-lg border border-slate-300 px-3 py-1.5 disabled:opacity-40"
      >
        다음
      </button>
    </div>
  );
}
