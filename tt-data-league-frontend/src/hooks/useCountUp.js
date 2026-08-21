import { useEffect, useMemo, useRef, useState } from 'react'

function easeOutExpo(progress) {
  if (progress >= 1) {
    return 1
  }

  return 1 - 2 ** (-10 * progress)
}

export function useCountUp(targetValue) {
  const target = useMemo(() => Number(targetValue || 0), [targetValue])
  const [displayValue, setDisplayValue] = useState(0)
  const [isVisible, setVisible] = useState(false)
  const observerRef = useRef(null)
  const elementRef = useRef(null)

  const prefersReducedMotion =
    typeof window !== 'undefined' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches

  useEffect(() => {
    if (!elementRef.current) {
      return undefined
    }

    observerRef.current = new IntersectionObserver(
      ([entry]) => {
        if (entry.isIntersecting) {
          setVisible(true)
          observerRef.current?.disconnect()
        }
      },
      { threshold: 0.3 },
    )

    observerRef.current.observe(elementRef.current)

    return () => observerRef.current?.disconnect()
  }, [])

  useEffect(() => {
    if (!isVisible) {
      return undefined
    }

    if (prefersReducedMotion) {
      const frameId = window.requestAnimationFrame(() => {
        setDisplayValue(target)
      })

      return () => window.cancelAnimationFrame(frameId)
    }

    const duration = 800
    let frameId
    let startTime

    function tick(timestamp) {
      if (!startTime) {
        startTime = timestamp
      }

      const elapsed = timestamp - startTime
      const progress = Math.min(elapsed / duration, 1)
      const eased = easeOutExpo(progress)
      setDisplayValue(Math.round(target * eased))

      if (progress < 1) {
        frameId = window.requestAnimationFrame(tick)
      }
    }

    frameId = window.requestAnimationFrame(tick)

    return () => window.cancelAnimationFrame(frameId)
  }, [isVisible, prefersReducedMotion, target])

  return { ref: elementRef, value: displayValue }
}
