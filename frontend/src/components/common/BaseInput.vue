<script setup>
import { useAttrs } from 'vue'

defineProps({
  modelValue: {
    type: [String, Number],
    default: '',
  },
  variant: {
    type: String,
    default: 'pill',
    validator: (value) => ['pill', 'field'].includes(value),
  },
  multiline: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['update:modelValue'])
const attrs = useAttrs()
</script>

<template>
  <textarea
    v-if="multiline"
    v-bind="attrs"
    :value="modelValue"
    class="w-full px-4 py-3 border-none bg-[#F7F7F7] rounded-xl text-sm outline-none resize-none transition-colors placeholder:text-[#BBBBBB] focus:bg-[#EEEEEE]"
    @input="emit('update:modelValue', $event.target.value)"
  />
  <input
    v-else
    v-bind="attrs"
    :value="modelValue"
    class="w-full px-4 border-none bg-[#F7F7F7] outline-none transition-colors duration-300 placeholder:text-[#BBBBBB] focus:bg-[#EEEEEE]"
    :class="variant === 'field' ? 'py-3 rounded-xl text-sm' : 'py-[14px] rounded-full text-base'"
    @input="emit('update:modelValue', $event.target.value)"
  />
</template>
