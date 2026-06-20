import { useEffect, useId, useRef, useState } from "react";

export type SelectOption<T extends string> = {
  value: T;
  label: string;
  description?: string;
};

export const DROPDOWN_TRIGGER_CLASSNAME =
  "flex h-9 w-full items-center justify-between rounded-md border border-input bg-background px-3 py-1 text-left text-xs shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-50";

export const DROPDOWN_CONTENT_CLASSNAME =
  "absolute z-50 mt-1 max-h-72 w-full overflow-auto rounded-md border border-border bg-popover p-1 text-popover-foreground shadow-md";

export const DROPDOWN_OPTION_CLASSNAME =
  "flex w-full cursor-default items-start justify-between gap-2 rounded-sm px-2 py-1.5 text-left text-xs outline-none transition-colors hover:bg-accent hover:text-accent-foreground";

type UseDropdownOptions = {
  optionCount: number;
  selectedIndex: number;
  closeOnSelect: boolean;
  onSelect: (index: number) => void;
};

export function useDropdown({ optionCount, selectedIndex, closeOnSelect, onSelect }: UseDropdownOptions) {
  const [isOpen, setIsOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);
  const containerRef = useRef<HTMLDivElement>(null);
  const triggerRef = useRef<HTMLButtonElement>(null);
  const optionRefs = useRef<Array<HTMLButtonElement | null>>([]);
  const listboxId = useId();

  function clampIndex(index: number) {
    if (optionCount <= 0) return -1;
    if (index < 0) return optionCount - 1;
    if (index >= optionCount) return 0;
    return index;
  }

  function openDropdown(index = selectedIndex >= 0 ? selectedIndex : 0) {
    if (optionCount <= 0) return;
    setIsOpen(true);
    setActiveIndex(clampIndex(index));
  }

  function closeDropdown(focusTrigger = false) {
    setIsOpen(false);
    setActiveIndex(-1);
    if (focusTrigger) {
      queueMicrotask(() => triggerRef.current?.focus());
    }
  }

  function moveActive(delta: number) {
    setActiveIndex(current =>
      clampIndex(current < 0 ? (selectedIndex >= 0 ? selectedIndex : 0) : current + delta)
    );
  }

  function selectIndex(index: number) {
    onSelect(index);
    if (closeOnSelect) {
      closeDropdown(true);
      return;
    }
    setActiveIndex(index);
  }

  function handleTriggerKeyDown(event: React.KeyboardEvent<HTMLButtonElement>) {
    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        if (isOpen) moveActive(1);
        else openDropdown(selectedIndex >= 0 ? selectedIndex : 0);
        break;
      case "ArrowUp":
        event.preventDefault();
        if (isOpen) moveActive(-1);
        else openDropdown(selectedIndex >= 0 ? selectedIndex : optionCount - 1);
        break;
      case "Enter":
      case " ":
        event.preventDefault();
        if (!isOpen) openDropdown(selectedIndex >= 0 ? selectedIndex : 0);
        break;
      case "Escape":
        if (isOpen) {
          event.preventDefault();
          closeDropdown();
        }
        break;
    }
  }

  function handleOptionKeyDown(event: React.KeyboardEvent<HTMLButtonElement>, index: number) {
    switch (event.key) {
      case "ArrowDown":
        event.preventDefault();
        moveActive(1);
        break;
      case "ArrowUp":
        event.preventDefault();
        moveActive(-1);
        break;
      case "Home":
        event.preventDefault();
        setActiveIndex(0);
        break;
      case "End":
        event.preventDefault();
        setActiveIndex(optionCount - 1);
        break;
      case "Enter":
      case " ":
        event.preventDefault();
        selectIndex(index);
        break;
      case "Escape":
        event.preventDefault();
        closeDropdown(true);
        break;
      case "Tab":
        closeDropdown();
        break;
    }
  }

  useEffect(() => {
    if (!isOpen) return;
    function handlePointerDown(event: MouseEvent) {
      if (!containerRef.current?.contains(event.target as Node)) {
        closeDropdown();
      }
    }
    document.addEventListener("mousedown", handlePointerDown);
    return () => document.removeEventListener("mousedown", handlePointerDown);
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen || activeIndex < 0) return;
    optionRefs.current[activeIndex]?.focus();
  }, [activeIndex, isOpen]);

  return {
    activeIndex,
    closeDropdown,
    containerRef,
    handleOptionKeyDown,
    handleTriggerKeyDown,
    isOpen,
    listboxId,
    openDropdown,
    optionRefs,
    selectIndex,
    setActiveIndex,
    setIsOpen,
    triggerRef
  };
}
