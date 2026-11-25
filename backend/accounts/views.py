from rest_framework import generics, permissions, status
from rest_framework.response import Response
from rest_framework.views import APIView
from rest_framework_simplejwt.tokens import RefreshToken

from .models import User
from .serializers import RegisterSerializer, UserSerializer


def tokens_for(user):
    refresh = RefreshToken.for_user(user)
    return {"access": str(refresh.access_token), "refresh": str(refresh)}


class RegisterView(generics.CreateAPIView):
    serializer_class = RegisterSerializer
    permission_classes = [permissions.AllowAny]

    def create(self, request, *args, **kwargs):
        ser = self.get_serializer(data=request.data)
        ser.is_valid(raise_exception=True)
        user = ser.save()
        return Response({"user": UserSerializer(user).data, **tokens_for(user)}, status=status.HTTP_201_CREATED)


class MeView(generics.RetrieveUpdateAPIView):
    serializer_class = UserSerializer
    permission_classes = [permissions.IsAuthenticated]

    def get_object(self):
        return self.request.user


class VerifyStudentView(APIView):
    """Prototype student verification: accepts a college email on an academic domain."""

    permission_classes = [permissions.IsAuthenticated]

    def post(self, request):
        email = (request.data.get("college_email") or "").strip().lower()
        if not User.is_student_domain(email):
            return Response({"detail": "Use your college email (ending in .ac.in, .edu or .edu.in)."},
                            status=status.HTTP_400_BAD_REQUEST)
        user = request.user
        user.college_email = email
        user.is_student_verified = True
        user.save(update_fields=["college_email", "is_student_verified"])
        return Response(UserSerializer(user).data)
