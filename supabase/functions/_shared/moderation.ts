// AgroMarket: Server-Side Chat Moderation & Leakage Prevention
// Pure function moderate(body) returning { blocked: boolean, reasons: string[] }
// Codes: PHONE, URL, SOCIAL, EMAIL, ADDRESS

export type ModerationReasonCode = "PHONE" | "URL" | "SOCIAL" | "EMAIL" | "ADDRESS";

export interface ModerationResult {
  blocked: boolean;
  reasons: ModerationReasonCode[];
}

const SPELLED_DIGITS: Record<string, string> = {
  zero: "0",
  one: "1",
  two: "2",
  three: "3",
  four: "4",
  five: "5",
  six: "6",
  seven: "7",
  eight: "8",
  nine: "9",
  // Sinhala transliterated digits
  binduwa: "0",
  eka: "1",
  deka: "2",
  thuna: "3",
  hathara: "4",
  paha: "5",
  haya: "6",
  hatha: "7",
  ata: "8",
  namaya: "9",
};

/**
 * Moderates a message body to prevent off-platform contact leaks.
 * Normalizes Unicode NFKC, lowercase, spelled and look-alike digits, and strips separators.
 * First removes standard produce trade expressions (e.g. "100 kg", "Rs. 250", "5 pm", "20%")
 * so normal agricultural marketplace talk is allowed without false alarms.
 */
export function moderate(rawBody: string): ModerationResult {
  const reasons: ModerationReasonCode[] = [];

  if (!rawBody || typeof rawBody !== "string") {
    return { blocked: false, reasons: [] };
  }

  // 1. Normalize Unicode (NFKC) and case
  const normalized = rawBody.normalize("NFKC").trim();
  const lower = normalized.toLowerCase();

  // 2. Pre-clean legitimate agricultural trade expressions from a copy before digit/phone analysis
  // Allows expressions like:
  // - "100 kg at 250 rate"
  // - "Rs. 1,250 per kg"
  // - "see you at 5 pm"
  // - "20% less?"
  const tradeSanitized = lower
    // Currency & Rates: "Rs. 1,250", "Rs 250", "1250 LKR", "250 rupees", "at 250 rate", "250 per kg", "250/kg"
    .replace(/\b(?:rs\.?|lkr|rupees)\s*[\d,]+(?:\.\d{1,2})?\b/gi, " ")
    .replace(/\b[\d,]+(?:\.\d{1,2})?\s*(?:rs\.?|lkr|rupees)\b/gi, " ")
    .replace(/\bat\s*\d+\s*(?:rate)?\b/gi, " ")
    .replace(/\b\d+\s*(?:\/|\s*per\s*)(?:kg|kilo|g|bundle|pack|box|acre)\b/gi, " ")
    // Produce quantities: "100 kg", "50kg", "25 kgs", "10 tons", "5 mt", "2 acres"
    .replace(/\b\d+(?:\.\d+)?\s*(?:kg|kgs|kilo|kilos|g|grams|mt|ton|tons|acre|acres|bundles|packs|boxes)\b/gi, " ")
    // Time references: "5 pm", "10:30 am", "at 5 pm"
    .replace(/\b(?:at\s*)?\b\d{1,2}(?::\d{2})?\s*(?:am|pm|a\.m\.|p\.m\.)\b/gi, " ")
    // Percentages: "20%"
    .replace(/\b\d+(?:\.\d+)?\s*%/g, " ");

  // 3. Email Detection (including standard and spelled "name at gmail dot com")
  const standardEmailRegex = /[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}/i;
  const spelledEmailRegex = /[a-zA-Z0-9._%+-]+\s+(?:at|@)\s+[a-zA-Z0-9.-]+\s+(?:dot|\.)\s+[a-zA-Z]{2,}/i;
  if (standardEmailRegex.test(lower) || spelledEmailRegex.test(lower)) {
    reasons.push("EMAIL");
  }

  // 4. Social / Messaging Platforms
  // whatsapp, viber, telegram, imo, fb, insta, wa.me, t.me
  const socialRegex = /\b(?:wa\.me|whatsapp|viber|telegram|t\.me|imo|messenger|facebook|fb\.com|fb\.me|instagram|insta|ig\.me)\b/i;
  if (socialRegex.test(lower)) {
    reasons.push("SOCIAL");
  }

  // 5. URLs and Spelled Domains
  // URLs and spelled "dot com", "dot lk", etc.
  const urlRegex = /(?:https?:\/\/|www\.)[^\s]+|\b[a-zA-Z0-9-]+\.(?:com|lk|org|net|me|io|co|app|xyz|info|biz|site|online)\b/i;
  const spelledUrlRegex = /\b[a-zA-Z0-9-]+\s+dot\s+(?:com|lk|org|net|me|io|co|app|xyz|info|biz|site|online)\b/i;
  if (urlRegex.test(lower) || spelledUrlRegex.test(lower)) {
    if (!reasons.includes("URL")) {
      reasons.push("URL");
    }
  }

  // 6. Street / Physical Address Patterns
  // Examples: "45 Kandy Rd", "No. 45", "12/A Galle Road", "Temple rd", "Station lane", "Mawatha", "Para"
  const addressRegex = /\b(?:no\.?\s*\d+|\d+[\s,/A-Za-z0-9-]*\s*(?:road|rd|street|st|lane|mawatha|mw|para|place|avenue|ave)\b|(?:road|rd|street|st|lane|mawatha|mw|para)\s*(?:no\.?)?\s*\d+)\b/i;
  if (addressRegex.test(lower)) {
    reasons.push("ADDRESS");
  }

  // 7. Spelled & Lookalike Digits and Phone Detection
  let digitNormalized = tradeSanitized;
  for (const [w, d] of Object.entries(SPELLED_DIGITS)) {
    digitNormalized = digitNormalized.replace(new RegExp(`\\b${w}\\b`, "gi"), d);
  }

  // Look-alike symbol conversion
  digitNormalized = digitNormalized
    .replace(/@/g, "a")
    .replace(/\$/g, "s")
    .replace(/!/g, "1");

  // Strip separators between digits to collapse phone numbers (e.g., "0 7 7 - 123 - 4567")
  const collapsedDigits = digitNormalized.replace(/[\s\-_.,/()+]/g, "");

  // Detect 9+ consecutive digit runs, or Sri Lankan mobile patterns (+94 / 0094 / 07x)
  const phonePatterns = [
    /(?:94|0094)?0?7[0-9]{8}/,
    /\d{9,}/
  ];

  for (const pattern of phonePatterns) {
    if (pattern.test(collapsedDigits)) {
      if (!reasons.includes("PHONE")) {
        reasons.push("PHONE");
      }
      break;
    }
  }

  return {
    blocked: reasons.length > 0,
    reasons,
  };
}
