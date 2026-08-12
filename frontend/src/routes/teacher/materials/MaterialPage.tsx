import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { TextField } from "../../../shared/components/TextField";
import { CATEGORY_LABELS, formatBytes } from "../../../shared/material/types";
import type { MaterialCategory } from "../../../shared/material/types";
import { uploadMaterial } from "../../../shared/material/upload";
import { deleteMaterial, listClassRooms, listMaterials } from "../api";
import { formatWeek } from "../format";

const NOW = new Date();
const CATEGORIES: MaterialCategory[] = ["LESSON", "TEXTBOOK", "PAST_EXAM", "ETC"];

/**
 * T-9 주차별 자료실 관리. 주차를 먼저 고르고 그 주의 자료를 올린다.
 *
 * <p><b>자료실은 학생만 본다.</b> 학부모 화면에 같은 목록을 붙이지 마라.
 */
export default function MaterialPage() {
  const queryClient = useQueryClient();
  const [year, setYear] = useState(NOW.getFullYear());
  const [month, setMonth] = useState(NOW.getMonth() + 1);
  const [week, setWeek] = useState<number | "">("");
  const [category, setCategory] = useState<MaterialCategory | "">("");
  const [uploading, setUploading] = useState(false);

  const params = {
    year,
    month,
    week: week === "" ? undefined : week,
    category: category === "" ? undefined : category,
  };
  const materials = useQuery({
    queryKey: ["teacher", "materials", params],
    queryFn: () => listMaterials(params),
  });

  const remove = useMutation({
    mutationFn: deleteMaterial,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ["teacher", "materials"] }),
  });

  const items = materials.data?.items ?? [];

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-slate-900">주차별 자료실 관리</h2>
        <button
          type="button"
          onClick={() => setUploading(true)}
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white"
        >
          자료 올리기
        </button>
      </div>

      <div className="grid grid-cols-2 gap-2 rounded-xl bg-white p-3 text-sm shadow-sm sm:grid-cols-4">
        <select
          value={year}
          onChange={(e) => setYear(Number(e.target.value))}
          className="rounded-lg border border-slate-300 px-2 py-1.5"
        >
          {[NOW.getFullYear() - 1, NOW.getFullYear(), NOW.getFullYear() + 1].map((y) => (
            <option key={y} value={y}>
              {y}년
            </option>
          ))}
        </select>
        <select
          value={month}
          onChange={(e) => setMonth(Number(e.target.value))}
          className="rounded-lg border border-slate-300 px-2 py-1.5"
        >
          {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
            <option key={m} value={m}>
              {m}월
            </option>
          ))}
        </select>
        <select
          value={week}
          onChange={(e) => setWeek(e.target.value === "" ? "" : Number(e.target.value))}
          className="rounded-lg border border-slate-300 px-2 py-1.5"
        >
          <option value="">전체 주차</option>
          {[1, 2, 3, 4, 5].map((w) => (
            <option key={w} value={w}>
              {w}주차
            </option>
          ))}
        </select>
        <select
          value={category}
          onChange={(e) => setCategory(e.target.value as MaterialCategory | "")}
          className="rounded-lg border border-slate-300 px-2 py-1.5"
        >
          <option value="">전체 분류</option>
          {CATEGORIES.map((value) => (
            <option key={value} value={value}>
              {CATEGORY_LABELS[value]}
            </option>
          ))}
        </select>
      </div>

      {remove.isError && <FormError message={errorMessage(remove.error)} />}

      {materials.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : items.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          이 주차에 올린 자료가 없습니다.
        </p>
      ) : (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl bg-white shadow-sm">
          {items.map((material) => (
            <li
              key={material.materialId}
              className="flex items-center justify-between gap-2 px-3 py-3"
            >
              <div className="min-w-0">
                <div className="flex items-center gap-1.5">
                  <Badge tone="neutral">{CATEGORY_LABELS[material.category]}</Badge>
                  {/* 공개 범위를 여기서 확인할 수 있어야 반 전용을 전체 공개로 잘못 올린 걸 잡는다 */}
                  {material.visibility === "PUBLIC" ? (
                    <Badge tone="warn">전체 공개</Badge>
                  ) : (
                    <Badge tone="ok">{material.classRoomName}</Badge>
                  )}
                  <span className="truncate text-sm font-medium text-slate-900">
                    {material.title}
                  </span>
                </div>
                <p className="mt-0.5 truncate text-xs text-slate-500">
                  {formatWeek(material.year, material.month, material.week)} · {material.fileName}
                  {material.bytes !== null && ` · ${formatBytes(material.bytes)}`}
                </p>
              </div>
              <button
                type="button"
                onClick={() => {
                  if (window.confirm(`"${material.title}"을 삭제할까요?`)) {
                    remove.mutate(material.materialId);
                  }
                }}
                className="shrink-0 text-xs text-slate-400 underline"
              >
                삭제
              </button>
            </li>
          ))}
        </ul>
      )}

      {uploading && (
        <UploadModal
          year={year}
          month={month}
          week={week === "" ? 1 : week}
          onClose={() => setUploading(false)}
          onDone={() => {
            void queryClient.invalidateQueries({ queryKey: ["teacher", "materials"] });
            setUploading(false);
          }}
        />
      )}
    </div>
  );
}

/**
 * 반을 다중 선택하고 <b>한 번의 저장</b>으로 여러 행을 만든다.
 * S3에는 파일을 한 번만 올리고 s3Key를 공유한다.
 *
 * <p>아무 반도 고르지 않으면 전체 공개(PUBLIC)다. "누구나"가 아니라
 * "로그인한 전체 재원생"이라는 뜻이다.
 */
function UploadModal({
  year,
  month,
  week,
  onClose,
  onDone,
}: {
  year: number;
  month: number;
  week: number;
  onClose: () => void;
  onDone: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState<MaterialCategory>("LESSON");
  const [selectedWeek, setSelectedWeek] = useState(week);
  const [selected, setSelected] = useState<number[]>([]);
  const [error, setError] = useState<string | null>(null);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });
  const rooms = (classRooms.data ?? []).filter((room) => room.status === "ACTIVE");

  const save = useMutation({
    mutationFn: () =>
      uploadMaterial(file!, {
        title: title.trim(),
        category,
        year,
        month,
        week: selectedWeek,
        classRoomIds: selected,
      }),
    onSuccess: onDone,
    onError: (err) => setError(err instanceof Error ? err.message : errorMessage(err)),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (!file) {
      setError("파일을 선택해 주세요.");
      return;
    }
    if (title.trim() === "") {
      setError("제목을 입력해 주세요.");
      return;
    }
    save.mutate();
  }

  return (
    <Modal title={`${year}년 ${month}월 자료 올리기`} onClose={onClose}>
      <form onSubmit={submit} className="space-y-3">
        <label className="block">
          <span className="block text-sm font-medium text-slate-700">파일</span>
          <input
            type="file"
            onChange={(e) => {
              const picked = e.target.files?.[0] ?? null;
              setFile(picked);
              // 제목을 아직 안 썼으면 파일명으로 채워 준다. 매번 두 번 입력할 이유가 없다
              if (picked && title.trim() === "") {
                setTitle(picked.name.replace(/\.[^.]+$/, ""));
              }
            }}
            className="mt-1 w-full text-sm"
          />
          <span className="mt-1 block text-xs text-slate-500">
            pdf · hwp · hwpx · docx · xlsx · pptx · zip · jpg · png, 50MB까지
          </span>
        </label>

        <TextField
          label="제목"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="A고 2학년 1학기 중간 기출"
        />

        <div className="grid grid-cols-2 gap-2">
          <label className="block text-sm text-slate-700">
            분류
            <select
              value={category}
              onChange={(e) => setCategory(e.target.value as MaterialCategory)}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            >
              {CATEGORIES.map((value) => (
                <option key={value} value={value}>
                  {CATEGORY_LABELS[value]}
                </option>
              ))}
            </select>
          </label>
          <label className="block text-sm text-slate-700">
            주차
            <select
              value={selectedWeek}
              onChange={(e) => setSelectedWeek(Number(e.target.value))}
              className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2"
            >
              {[1, 2, 3, 4, 5].map((w) => (
                <option key={w} value={w}>
                  {w}주차
                </option>
              ))}
            </select>
          </label>
        </div>

        <div>
          <p className="mb-1 text-sm font-medium text-slate-700">
            대상 반 ({selected.length}개 선택)
          </p>
          <div className="space-y-1">
            <button
              type="button"
              onClick={() =>
                setSelected(selected.length === rooms.length ? [] : rooms.map((r) => r.classRoomId))
              }
              className="text-xs text-slate-500 underline"
            >
              {selected.length === rooms.length ? "전체 해제" : "전체 선택"}
            </button>
            <ul className="max-h-44 space-y-1 overflow-y-auto">
              {rooms.map((room) => (
                <li key={room.classRoomId}>
                  <label className="flex items-center gap-2 text-sm text-slate-700">
                    <input
                      type="checkbox"
                      checked={selected.includes(room.classRoomId)}
                      onChange={(e) =>
                        setSelected((prev) =>
                          e.target.checked
                            ? [...prev, room.classRoomId]
                            : prev.filter((id) => id !== room.classRoomId),
                        )
                      }
                      className="h-4 w-4 rounded border-slate-300"
                    />
                    {room.name}
                  </label>
                </li>
              ))}
            </ul>
          </div>
          <p className="mt-1 text-xs text-slate-500">
            {selected.length === 0
              ? "아무 반도 고르지 않으면 전체 재원생에게 공개됩니다."
              : `반마다 한 행이 만들어집니다. 파일은 한 번만 올라갑니다.`}
          </p>
        </div>

        <FormError message={error} />

        <SubmitButton pending={save.isPending} disabled={!file}>
          {selected.length === 0 ? "전체 공개로 올리기" : `${selected.length}개 반에 올리기`}
        </SubmitButton>
      </form>
    </Modal>
  );
}
