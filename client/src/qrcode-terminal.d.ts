declare module "qrcode-terminal" {
  const qrcode: {
    generate(input: string, options?: { small?: boolean }, callback?: (qr: string) => void): void;
  };
  export default qrcode;
}
