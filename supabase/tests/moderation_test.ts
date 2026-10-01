// Deno Unit Test for Chat Moderation
import { assertEquals } from "https://deno.land/std@0.168.0/testing/asserts.ts";
import { moderate } from "../functions/_shared/moderation.ts";

Deno.test("Block: raw phone number", () => {
  const res = moderate("0771234567");
  assertEquals(res.blocked, true);
  assertEquals(res.reasons.includes("PHONE"), true);
});

Deno.test("Block: spelled phone digits", () => {
  const res = moderate("zero seven seven one two three four five six seven");
  assertEquals(res.blocked, true);
  assertEquals(res.reasons.includes("PHONE"), true);
});

Deno.test("Block: separated phone number", () => {
  const res = moderate("0 7 7 - 123 - 4567");
  assertEquals(res.blocked, true);
  assertEquals(res.reasons.includes("PHONE"), true);
});

Deno.test("Block: international format phone number", () => {
  const res = moderate("+94 77 123 4567");
  assertEquals(res.blocked, true);
  assertEquals(res.reasons.includes("PHONE"), true);
});

Deno.test("Block: WhatsApp link (social/url/phone)", () => {
  const res = moderate("wa.me/94771234567");
  assertEquals(res.blocked, true);
  assertEquals(res.reasons.includes("SOCIAL"), true);
});

Deno.test("Block: spelled email address", () => {
  const res = moderate("name at gmail dot com");
  assertEquals(res.blocked, true);
  assertEquals(res.reasons.includes("EMAIL"), true);
});

Deno.test("Block: physical street address", () => {
  const res = moderate("45 Kandy Rd");
  assertEquals(res.blocked, true);
  assertEquals(res.reasons.includes("ADDRESS"), true);
});

Deno.test("Block: house number pattern", () => {
  const res = moderate("No. 45");
  assertEquals(res.blocked, true);
  assertEquals(res.reasons.includes("ADDRESS"), true);
});

// MUST ALLOW:
Deno.test("Allow: quantity and rate trade discussion", () => {
  const res = moderate("100 kg at 250 rate");
  assertEquals(res.blocked, false);
  assertEquals(res.reasons.length, 0);
});

Deno.test("Allow: formatted price per kg", () => {
  const res = moderate("Rs. 1,250 per kg");
  assertEquals(res.blocked, false);
  assertEquals(res.reasons.length, 0);
});

Deno.test("Allow: time arrangement", () => {
  const res = moderate("see you at 5 pm");
  assertEquals(res.blocked, false);
  assertEquals(res.reasons.length, 0);
});

Deno.test("Allow: discount percentage negotiation", () => {
  const res = moderate("20% less?");
  assertEquals(res.blocked, false);
  assertEquals(res.reasons.length, 0);
});
