import { useCallback, useEffect, useRef, useState } from 'react';
import { loadLiteRt, loadAndCompile, Tensor, CompiledModel } from '@litertjs/core';
import { imageFileToTensor } from '../utils/preprocess';
import type { ReportStatus } from '../types/laporan';

const LABELS: ReportStatus[] = ['AMAN', 'WASPADA', 'BAHAYA'];
const MODEL_URL = '/models/retak/retak_mobilenetv2.tflite';
const WASM_PATH = '/wasm/';

interface PredictionResult {
  status: ReportStatus;
  confidence: number;
  probabilities: number[];
}

interface UseModelInferenceReturn {
  isModelReady: boolean;
  isPredicting: boolean;
  modelError: string | null;
  predict: (file: File) => Promise<PredictionResult>;
}

// Module-level singleton: loadLiteRt only once across all hook instances
let liteRtPromise: Promise<unknown> | null = null;
let liteRtLoaded = false;

function getLiteRt(): Promise<unknown> {
  if (liteRtLoaded) return Promise.resolve();
  if (liteRtPromise) return liteRtPromise;

  liteRtPromise = loadLiteRt(WASM_PATH)
    .then(() => { liteRtLoaded = true; })
    .catch((err) => {
      liteRtPromise = null;
      throw err;
    });

  return liteRtPromise;
}

export function useModelInference(): UseModelInferenceReturn {
  const modelRef = useRef<CompiledModel | null>(null);
  const [isModelReady, setIsModelReady] = useState(false);
  const [isPredicting, setIsPredicting] = useState(false);
  const [modelError, setModelError] = useState<string | null>(null);

  useEffect(() => {
    let disposed = false;

    async function init() {
      try {
        await getLiteRt();
        if (disposed) return;

        const compiled = await loadAndCompile(MODEL_URL);
        if (disposed) { compiled.delete(); return; }

        // Warmup — INT8 model expects UInt8 [0-255]
        const warmup = Tensor.fromTypedArray(new Uint8Array(224 * 224 * 3), [1, 224, 224, 3]);
        await compiled.run(warmup);
        warmup.delete();

        modelRef.current = compiled;
        setIsModelReady(true);
        setModelError(null);
      } catch (err) {
        if (disposed) return;
        const msg = err instanceof Error ? err.message : 'Gagal memuat model';
        console.error('Model init error:', msg, err);
        setModelError(msg);
      }
    }

    init();

    return () => {
      disposed = true;
      modelRef.current?.delete();
      modelRef.current = null;
    };
  }, []);

  const predict = useCallback(async (file: File): Promise<PredictionResult> => {
    const model = modelRef.current;
    if (!model) throw new Error('Model belum siap');

    setIsPredicting(true);
    try {
      const { data } = await imageFileToTensor(file);
      // INT8 model expects UInt8 directly [0-255]
      const input = Tensor.fromTypedArray(data, [1, 224, 224, 3]);

      const outputs = await model.run(input);
      const output = outputs[0];
      const raw = await output.data();

      input.delete();
      output.delete();

      const logits = Array.from(raw);
      const maxLogit = Math.max(...logits);
      const exps = logits.map((v) => Math.exp(v - maxLogit));
      const sumExps = exps.reduce((a, b) => a + b, 0);
      const probs = exps.map((v) => v / sumExps);
      const argmax = probs.indexOf(Math.max(...probs));

      return {
        status: LABELS[argmax],
        confidence: probs[argmax],
        probabilities: probs,
      };
    } finally {
      setIsPredicting(false);
    }
  }, []);

  return { isModelReady, isPredicting, modelError, predict };
}
