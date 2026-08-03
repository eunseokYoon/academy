export function FormError({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p
      role="alert"
      className="rounded-xl bg-red-50 px-3 py-2 text-sm text-red-700 ring-1 ring-inset
                 ring-red-200"
    >
      {message}
    </p>
  );
}
