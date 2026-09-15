import { useRef, useState } from "react";
import type { FormEvent } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { errorMessage } from "../../../shared/api/errors";
import { Badge } from "../../../shared/components/Badge";
import { FormError } from "../../../shared/components/FormError";
import { Modal } from "../../../shared/components/Modal";
import { SubmitButton } from "../../../shared/components/SubmitButton";
import { TextAreaField } from "../../../shared/components/TextAreaField";
import { TextField } from "../../../shared/components/TextField";
import type { AttachmentDraft } from "../../../shared/notice/attachmentUpload";
import { uploadNoticeAttachment } from "../../../shared/notice/attachmentUpload";
import {
  createNotice,
  deleteNotice,
  listClassRooms,
  listTeacherNotices,
  publishNotice,
  updateNotice,
} from "../api";
import type { NoticeAudience, TeacherNotice } from "../api";

const MAX_ATTACHMENTS = 5;

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${Math.round(bytes / 1024)}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
}

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
  const [viewing, setViewing] = useState<TeacherNotice | null>(null);

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
                <button
                  type="button"
                  onClick={() => setViewing(notice)}
                  className="min-w-0 flex-1 text-left"
                >
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
                </button>
                <div className="flex shrink-0 flex-col items-end gap-1">
                  {notice.publishedAt === null && (
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        publish.mutate(notice.noticeId);
                      }}
                      className="rounded-lg bg-slate-900 px-2.5 py-1.5 text-xs font-medium
                                 text-white"
                    >
                      발행
                    </button>
                  )}
                  <div className="flex gap-2">
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
                        setEditing(notice);
                      }}
                      className="text-xs text-slate-500 underline"
                    >
                      수정
                    </button>
                    <button
                      type="button"
                      onClick={(e) => {
                        e.stopPropagation();
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

      {viewing && (
        <Modal title={viewing.title} onClose={() => setViewing(null)}>
          <div className="flex flex-wrap items-center gap-1.5">
            {viewing.pinned && <Badge tone="warn">고정</Badge>}
            {viewing.publishedAt === null ? (
              <Badge tone="danger">초안</Badge>
            ) : (
              <Badge tone="ok">발행</Badge>
            )}
            <Badge tone="neutral">
              {viewing.scope === "ALL"
                ? "전체"
                : viewing.scope === "STUDENT"
                  ? `${viewing.studentName} 개인`
                  : viewing.classRoomName}
            </Badge>
          </div>
          <p className="mt-3 whitespace-pre-wrap text-sm text-slate-700">{viewing.content}</p>
          {(viewing.attachments ?? []).map((attachment) => (
            <p key={attachment.attachmentId} className="mt-2 text-[13px] text-slate-600">
              📎 {attachment.fileName}
            </p>
          ))}
        </Modal>
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
  /* 옛 응답에는 audience가 없다. ?? "ALL"이 없으면 undefined가 라디오를 하나도
     선택하지 않은 상태로 만들고, 그대로 저장하면 400이다 */
  const [audience, setAudience] = useState<NoticeAudience>(notice?.audience ?? "ALL");
  const [attachments, setAttachments] = useState<AttachmentDraft[]>([]);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [error, setError] = useState<string | null>(null);

  const classRooms = useQuery({
    queryKey: ["teacher", "class-rooms"],
    queryFn: () => listClassRooms(),
  });
  const rooms = (classRooms.data ?? []).filter((room) => room.status === "ACTIVE");

  /**
   * 파일을 붙이면 「학생만」으로 옮긴다 — 실수하는 방향이 유출 쪽이면 안 된다.
   * 선생님이 다시 고를 수 있다. 학부모 전용 공지에 파일을 붙이려면 순서를
   * 바꿔서, 파일을 먼저 올리고 대상을 「학부모만」으로 고르면 된다
   */
  const addFiles = async (files: File[]) => {
    if (files.length === 0) return;
    if (attachments.length + files.length > MAX_ATTACHMENTS) {
      setError(`첨부 파일은 최대 ${MAX_ATTACHMENTS}개까지 올릴 수 있습니다.`);
      return;
    }
    setError(null);
    setUploading(true);
    try {
      const uploaded = await Promise.all(files.map(uploadNoticeAttachment));
      setAttachments((prev) => [...prev, ...uploaded]);
      setAudience("STUDENT_ONLY");
    } catch (e) {
      setError(errorMessage(e, "파일을 올리지 못했습니다."));
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = "";
    }
  };

  const removeAttachment = (index: number) => {
    setAttachments((prev) => prev.filter((_, i) => i !== index));
  };

  const save = useMutation({
    mutationFn: async () => {
      const body = { title: title.trim(), content: content.trim(), pinned, audience };
      const attachmentPayload = attachments.map(({ s3Key, fileName, bytes }) => ({
        s3Key,
        fileName,
        bytes,
      }));
      if (notice) {
        await updateNotice(notice.noticeId, {
          ...body,
          scope: scopeAll ? "ALL" : "CLASS",
          classRoomId: scopeAll ? null : selected[0],
          // 손대지 않았으면 attachments를 아예 보내지 않는다 — 빈 배열은 "전부 지운다"는
          // 뜻이라, 파일을 안 건드렸는데 보내면 기존 첨부가 사라진다.
          ...(attachments.length > 0 ? { attachments: attachmentPayload } : {}),
        });
        return;
      }
      if (scopeAll) {
        await createNotice({
          ...body,
          scope: "ALL",
          classRoomId: null,
          attachments: attachmentPayload,
        });
        return;
      }
      // 반마다 한 행이다. 반을 여러 개 고르면 그만큼 만든다
      for (const classRoomId of selected) {
        await createNotice({ ...body, scope: "CLASS", classRoomId, attachments: attachmentPayload });
      }
    },
    onSuccess: onDone,
    onError: (err) => setError(errorMessage(err)),
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    if (uploading) {
      // 업로드가 끝나기 전에 저장하면 방금 고른 파일이 attachments에 안 실린다
      setError("파일을 올리는 중입니다. 잠시 후 다시 시도해 주세요.");
      return;
    }
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

        <div>
          <p className="mb-1 text-sm font-medium text-slate-700">첨부 파일</p>

          {/*
            수정 중이고 아직 이번 세션에서 파일을 안 건드렸다면, 기존 첨부가 있다는 걸
            알려준다 — 여기서 파일을 새로 올리면 그 순간 기존 첨부는 전부 대체된다
            (attachments의 s3Key를 서버가 내려주지 않아 기존 파일과 합칠 방법이 없다).
          */}
          {notice && notice.attachments.length > 0 && attachments.length === 0 && (
            <p className="mb-1.5 text-xs text-slate-500">
              현재 첨부 {notice.attachments.length}개. 여기서 파일을 추가하면 기존 파일이
              전부 새로 올린 파일로 바뀝니다.
            </p>
          )}

          {attachments.length > 0 && (
            <ul className="mb-1.5 space-y-1">
              {attachments.map((file, index) => (
                <li
                  key={`${file.s3Key}-${index}`}
                  className="flex items-center justify-between gap-2 rounded-lg bg-slate-50
                             px-2.5 py-1.5 text-xs text-slate-700"
                >
                  <span className="min-w-0 truncate">{file.fileName}</span>
                  <span className="flex shrink-0 items-center gap-2">
                    <span className="tnum text-slate-400">{formatBytes(file.bytes)}</span>
                    <button
                      type="button"
                      onClick={() => removeAttachment(index)}
                      aria-label="첨부 빼기"
                      className="text-slate-400"
                    >
                      ✕
                    </button>
                  </span>
                </li>
              ))}
            </ul>
          )}

          <button
            type="button"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading || attachments.length >= MAX_ATTACHMENTS}
            className="rounded-lg border border-dashed border-slate-300 px-3 py-2 text-xs
                       text-slate-600 disabled:opacity-50"
          >
            {uploading ? "올리는 중…" : "파일 추가"}
          </button>
          <input
            ref={fileInputRef}
            type="file"
            multiple
            hidden
            onChange={(e) => void addFiles(Array.from(e.target.files ?? []))}
          />
          <p className="mt-1 text-xs text-slate-400">
            파일은 최대 {MAX_ATTACHMENTS}개, 개당 50MB까지입니다.
          </p>
        </div>

        <fieldset>
          <legend className="text-sm font-medium text-slate-700">공지 대상</legend>
          <div className="mt-1.5 space-y-1.5">
            {(
              [
                ["ALL", "학생과 학부모", "둘 다 봅니다."],
                ["STUDENT_ONLY", "학생만", "학부모에게 보이지 않습니다."],
                ["PARENT_ONLY", "학부모만", "학생에게 보이지 않습니다."],
              ] as const
            ).map(([value, label, hint]) => (
              <label key={value} className="flex items-start gap-2 text-sm">
                <input
                  type="radio"
                  name="notice-audience"
                  value={value}
                  checked={audience === value}
                  onChange={() => setAudience(value)}
                  className="mt-0.5"
                />
                <span>
                  {label}
                  <span className="block text-xs text-brand-500">{hint}</span>
                </span>
              </label>
            ))}
          </div>
        </fieldset>

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

        <SubmitButton pending={save.isPending} disabled={uploading}>
          {notice ? "저장" : !scopeAll && selected.length > 1 ? `${selected.length}개 반에 작성` : "초안 저장"}
        </SubmitButton>
      </form>
    </Modal>
  );
}
