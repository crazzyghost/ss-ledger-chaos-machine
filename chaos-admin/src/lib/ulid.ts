// Minimal client-side ULID generator (Crockford base32, 26 chars): a 48-bit millisecond timestamp
// (10 chars) followed by 80 bits of randomness (16 chars). Used to seed ULID-autogen reference
// fields (merchant_ref_id, provider_reference_id, narration, …). The server re-mints a ULID when a
// field is left blank, and the ledger treats these as opaque strings, so strict monotonicity is not
// required here.

const ENCODING = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"; // Crockford base32 (no I, L, O, U)

export function ulid(): string {
  let timestamp = Date.now();
  let time = "";
  for (let i = 0; i < 10; i++) {
    time = ENCODING[timestamp % 32] + time;
    timestamp = Math.floor(timestamp / 32);
  }

  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  let random = "";
  for (let i = 0; i < 16; i++) {
    random += ENCODING[bytes[i] % 32];
  }

  return time + random;
}
