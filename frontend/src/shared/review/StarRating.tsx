const STAR_INDEXES = [1, 2, 3, 4, 5] as const;

/** 별 하나의 도형. viewBox 24x24, 채운 별이라 stroke가 아니라 fill을 쓴다. */
const STAR_PATH =
  "M12 2.5l2.95 5.98 6.6.96-4.78 4.66 1.13 6.58L12 17.77l-5.9 3.1 1.13-6.58L2.45 9.44l6.6-.96z";

function StarGlyph({ className }: { className: string }) {
  return (
    <svg viewBox="0 0 24 24" className={className} fill="currentColor" aria-hidden="true">
      <path d={STAR_PATH} />
    </svg>
  );
}

interface StarHalfProps {
  side: "left" | "right";
  label: string;
  readOnly: boolean;
  onSelect: () => void;
}

/**
 * 별 한 칸의 좌/우 절반. 입력 모드는 button, 표시 모드는 span이다.
 * 표시 모드에 button을 두면 스크린리더가 눌러도 되는 것으로 읽는다.
 */
function StarHalf({ side, label, readOnly, onSelect }: StarHalfProps) {
  const position = side === "left" ? "left-0" : "right-0";
  if (readOnly) {
    return <span aria-hidden="true" className={`absolute inset-y-0 ${position} w-1/2`} />;
  }
  return (
    <button
      type="button"
      aria-label={label}
      onClick={onSelect}
      className={`absolute inset-y-0 ${position} w-1/2`}
    />
  );
}

/**
 * 별 한 칸. 채워질 비율(0 / 0.5 / 1)만큼 amber 레이어를 왼쪽부터 클립해서
 * slate 바탕 별 위에 겹친다 — 반쪽 별 글리프를 따로 구하는 대신 쓰는 방식이다.
 */
function Star({
  index,
  value,
  readOnly,
  size,
  onSelect,
}: {
  index: number;
  value: number;
  readOnly: boolean;
  size: string;
  onSelect: (value: number) => void;
}) {
  const fill = Math.min(1, Math.max(0, value - (index - 1)));

  return (
    <div className={`relative shrink-0 ${size}`}>
      <StarGlyph className={`absolute inset-0 text-slate-200 ${size}`} />
      <div className="absolute inset-y-0 left-0 overflow-hidden" style={{ width: `${fill * 100}%` }}>
        <StarGlyph className={`text-amber-400 ${size}`} />
      </div>
      <StarHalf
        side="left"
        label={`${index - 0.5}점`}
        readOnly={readOnly}
        onSelect={() => onSelect(index - 0.5)}
      />
      <StarHalf side="right" label={`${index}점`} readOnly={readOnly} onSelect={() => onSelect(index)} />
    </div>
  );
}

interface StarRatingInputProps {
  value: number;
  onChange: (value: number) => void;
  readOnly?: false;
}

interface StarRatingDisplayProps {
  value: number;
  onChange?: undefined;
  readOnly: true;
}

type StarRatingProps = StarRatingInputProps | StarRatingDisplayProps;

/**
 * 0.5 단위 별점. 별 하나를 좌/우 절반으로 나눠 탭한다.
 *
 * <b>크기가 두 가지고, 둘을 같이 움직이지 마라.</b>
 *
 * 입력 모드는 56px다. 반쪽 터치 타깃이 WCAG 2.5.8 AA 최소 24px를 넘어야 하고,
 * 3.5와 4.0을 가르는 경계선은 정확해야 해서 히트슬롭으로 옆 절반을 침범할 수 없다 —
 * 그래서 별 자체를 키우는 수밖에 없고 56px가 절반 28px로 그 바닥선을 넘기는 최소치다.
 * 옆에 숫자를 같이 띄우는 것도 같은 이유다. 숫자가 없으면 자기가 뭘 골랐는지 확신하지 못한다.
 * <b>입력 모드를 줄이지 마라.</b>
 *
 * 표시 모드는 20px다. 누를 곳이 없으니(반쪽이 span이다) 터치 타깃 규칙이 아예 적용되지
 * 않는다. 선생님 후기 목록에서는 <b>본문이 주인공</b>이라, 별이 크면 텍스트를 눌러 버린다.
 */
export function StarRating(props: StarRatingProps) {
  const { value } = props;
  const readOnly = props.readOnly === true;
  const onSelect = props.readOnly ? () => {} : props.onChange;
  const size = readOnly ? "h-5 w-5" : "h-14 w-14";

  return (
    <div
      className={`flex items-center ${readOnly ? "gap-1.5" : "gap-2"}`}
      role={readOnly ? "img" : undefined}
      aria-label={readOnly ? `${value.toFixed(1)}점` : undefined}
    >
      <div className="flex">
        {STAR_INDEXES.map((index) => (
          <Star
            key={index}
            index={index}
            value={value}
            readOnly={readOnly}
            size={size}
            onSelect={onSelect}
          />
        ))}
      </div>
      <span
        aria-hidden={readOnly || undefined}
        className={`tnum font-bold text-brand-900 ${readOnly ? "text-xs" : "text-[15px]"}`}
      >
        {value.toFixed(1)}
      </span>
    </div>
  );
}
