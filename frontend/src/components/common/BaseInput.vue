<script setup>
import { useAttrs } from 'vue';

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
});

const emit = defineEmits(['update:modelValue']);
const attrs = useAttrs();
</script>

<template>
  <textarea
    v-if="multiline"
    v-bind="attrs"
    :value="modelValue"
    class="control-field control-field-block resize-none"
    @input="emit('update:modelValue', $event.target.value)"
  />
  <input
    v-else
    v-bind="attrs"
    :value="modelValue"
    class="control-field"
    :class="variant === 'field' ? 'control-field-block' : 'control-field-pill'"
    @input="emit('update:modelValue', $event.target.value)"
  />
</template>
