import { useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { TextAreaField } from "../../../shared/components/TextAreaField";
import { TextField } from "../../../shared/components/TextField";
import {
  createNotice,
  deleteNotice,
  listClassRooms,
  listTeacherNotices,
  publishNotice,
  updateNotice,
} from "../api";
import type { TeacherNotice } from "../api";

/**
 * T-10 공지 관리. 작성 → (수정) → 발행 순서다.
 *
 * <p><b>발행 전에는 초안</b>이라 학생·학부모에게 보이지 않는다. 목록에서 초안이 눈에
 * 띄어야 "썼는데 안 나간" 공지를 찾을 수 있다.
 *
 * <p>본문은 일반 텍스트다. 리치 에디터나 마크다운을 붙이지 마라.
 */
export default function NoticePage() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<TeacherNotice | "new" | null>(null);

  const notices = useQuery({
    queryKey: ["teacher", "notices"],
    queryFn: () => listTeacherNotices({}),
  });

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ["teacher", "notices"] });
    void queryClient.invalidateQueries({ queryKey: ["notices"] });
  };

  const publish = useMutation({ mutationFn: publishNotice, onSuccess: invalidate });
  const remove = useMutation({ mutationFn: deleteNotice, onSuccess: invalidate });

  const items = notices.data?.items ?? [];

  return (
    <div className="space-y-4 md:max-w-3xl">
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-lg font-semibold text-slate-900">공지 관리</h2>
        <button
          type="button"
          onClick={() => setEditing("new")}
          className="rounded-lg bg-slate-900 px-3 py-2 text-sm font-medium text-white"
        >
          공지 작성
        </button>
      </div>

      {(publish.isError || remove.isError) && (
        <FormError message={errorMessage(publish.error ?? remove.error)} />
      )}

      {notices.isPending ? (
        <p className="text-sm text-slate-400">불러오는 중…</p>
      ) : items.length === 0 ? (
        <p className="rounded-xl bg-white p-6 text-center text-sm text-slate-500 shadow-sm">
          작성한 공지가 없습니다.
        </p>
      ) : (
        <ul className="divide-y divide-slate-100 overflow-hidden rounded-xl bg-white shadow-sm">
          {items.map((notice) => (
            <li key={notice.noticeId} className="px-3 py-3">
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-1.5">
                    {notice.pinned && <Badge tone="warn">고정</Badge>}
                    {/* 초안이 눈에 띄어야 "썼는데 안 나간" 공지를 찾는다 */}
                    {notice.publishedAt === null ? (
                      <Badge tone="danger">초안</Badge>
                    ) : (
                      <Badge tone="ok">발행</Badge>
                    )}
                    {/*
                      STUDENT는 수업일 변경 승인이 만든 개인 공지다. classRoomName이
                      null이라 이 분기가 없으면 배지가 빈 칸으로 뜬다.
                    */}
                    <Badge tone="neutral">
                      {notice.scope === "ALL"
                        ? "전체"
                        : notice.scope === "STUDENT"
                          ? `${notice.studentName} 개인`
                          : notice.classRoomName}
                    </Badge>
                    <span className="truncate text-sm font-medium text-slate-900">
                      {notice.title}
                    </span>
                  </div>
                  <p className="mt-1 line-clamp-2 whitespace-pre-wrap text-xs text-slate-500">
                    {notice.content}
                  </p>
                </div>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  {notice.publishedAt === null && (
                    <button
                      type="button"
                      onClick={() => publish.mutate(notice.noticeId)}
                      className="rounded-lg bg-slate-900 px-2.5 py-1.5 text-xs font-medium
                                 text-white"
                    >
                      발행
                    </button>
                  )}
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={() => setEditing(notice)}
                      className="text-xs text-slate-500 underline"
                    >
                      수정
                    </button>
                    <button
                      type="button"
                      onClick={() => {
                        if (
                          window.confirm(
                            notice.publishedAt === null
                              ? `초안 "${notice.title}"을 삭제할까요?`
                              : `"${notice.title}"을 삭제할까요? 학생·학부모 화면에서도 사라집니다.`,
                          )
                        ) {
                          remove.mutate(notice.noticeId);
                        }
                      }}
                      className="text-xs text-slate-400 underline"
                    >
                      삭제
                    </button>
                  </div>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {editing !== null && (
        <NoticeModal
          notice={editing === "new" ? null : editing}
          onClose={() => setEditing(null)}
          onDone={() => {
            invalidate();
            setEditing(null);
          }}
        />
      )}
    </div>
  );
}

/**
 * 새 공지는 반을 다중 선택해 <b>반마다 한 행</b>을 만든다.
 * 수정은 대상이 하나로 고정된 행 하나만 다룬다.
 */
function NoticeModal({
  notice,
  onClose,
  onDone,
}: {
  notice: TeacherNotice | null;
  onClose: () => void;
  onDone: () => void;
}) {
  const [title, setTitle] = useState(notice?.title ?? "");
  const [content, setContent] = useState(notice?.content ?? "");
  const [pinned, setPinned] = useState(notice?.pinned ?? false);
  const [scopeAll, setScopeAll] = useState(notice ? notice.scope === "ALL" : true);
  const [selected, setSelected] = useState<number[]>(
    notice?.classRoomId ? [notice.classRoomId] : [],
  );
  const [error, setError] = useState<string | null>(null);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });
  const rooms = (classRooms.data ?? []).filter((room) => room.status === "ACTIVE");

  const save = useMutation({
    mutationFn: async () => {
      const body = { title: title.trim(), content: content.trim(), pinned };
      if (notice) {
        await updateNotice(notice.noticeId, {
          ...body,
          scope: scopeAll ? "ALL" : "CLASS",
          classRoomId: scopeAll ? null : selected[0],
        });
        return;
      }
      if (scopeAll) {
        await createNotice({ ...body, scope: "ALL", classRoomId: null });
        return;
      }
      // 반마다 한 행이다. 반을 여러 개 고르면 그만큼 만든다
      for (const classRoomId of selected) {
        await createNotice({ ...body, scope: "CLASS", classRoomId });
      }
    },
    onSuccess: onDone,
    onError: (err) => setError(errorMessage(err)),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (title.trim() === "" || content.trim() === "") {
      setError("제목과 내용을 입력해 주세요.");
      return;
    }
    if (!scopeAll && selected.length === 0) {
      setError("대상 반을 선택해 주세요.");
      return;
    }
    save.mutate();
  }

  return (
    <Modal title={notice ? "공지 수정" : "공지 작성"} onClose={onClose}>
      <form onSubmit={submit} className="space-y-3">
        <TextField
          label="제목"
          value={title}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="[SUMMER] 고1 영어 구문독해 선행 안내"
        />

        <TextAreaField
          label="내용"
          value={content}
          onChange={(e) => setContent(e.target.value)}
          rows={6}
          hint="일반 텍스트로 표시됩니다. 줄바꿈만 유지됩니다."
        />

        <div>
          <p className="mb-1 text-sm font-medium text-slate-700">대상</p>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => setScopeAll(true)}
              className={`flex-1 rounded-lg px-3 py-2 text-sm ${
                scopeAll ? "bg-slate-900 text-white" : "border border-slate-300 text-slate-700"
              }`}
            >
              전체
            </button>
            <button
              type="button"
              onClick={() => setScopeAll(false)}
              className={`flex-1 rounded-lg px-3 py-2 text-sm ${
                scopeAll ? "border border-slate-300 text-slate-700" : "bg-slate-900 text-white"
              }`}
            >
              특정 반
            </button>
          </div>
        </div>

        {!scopeAll && (
          <ul className="max-h-40 space-y-1 overflow-y-auto">
            {rooms.map((room) => (
              <li key={room.classRoomId}>
                <label className="flex items-center gap-2 text-sm text-slate-700">
                  <input
                    // 수정은 대상 행이 하나라 라디오, 작성은 반마다 행을 만들 수 있어 체크박스
                    type={notice ? "radio" : "checkbox"}
                    checked={selected.includes(room.classRoomId)}
                    onChange={(e) =>
                      setSelected((prev) => {
                        if (notice) return [room.classRoomId];
                        return e.target.checked
                          ? [...prev, room.classRoomId]
                          : prev.filter((id) => id !== room.classRoomId);
                      })
                    }
                    className="h-4 w-4 border-slate-300"
                  />
                  {room.name}
                </label>
              </li>
            ))}
          </ul>
        )}

        <label className="flex items-center gap-2 text-sm text-slate-700">
          <input
            type="checkbox"
            checked={pinned}
            onChange={(e) => setPinned(e.target.checked)}
            className="h-4 w-4 rounded border-slate-300"
          />
          목록 상단에 고정
        </label>

        <FormError message={error} />

        {!notice && (
          <p className="text-xs text-slate-500">
            저장하면 <b>초안</b>으로 남습니다. 목록에서 발행을 눌러야 학생·학부모에게 보입니다.
          </p>
        )}

        <SubmitButton pending={save.isPending}>
          {notice ? "저장" : !scopeAll && selected.length > 1 ? `${selected.length}개 반에 작성` : "초안 저장"}
        </SubmitButton>
      </form>
    </Modal>
  );
}
